import asyncio
import json
from typing import TYPE_CHECKING, List

import aiohttp
import opuslib_next
import websockets

from config.logger import setup_logging
from core.providers.asr.base import ASRProviderBase
from core.providers.asr.dto.dto import InterfaceType

if TYPE_CHECKING:
    from core.connection import ConnectionHandler


TAG = __name__
logger = setup_logging()

SHORT_UTTERANCE_LANGUAGE_FALLBACKS = {
    "bn": ["bn", "ar"],
}


class ASRProvider(ASRProviderBase):
    def __init__(self, config, delete_audio_file):
        super().__init__()
        self.interface_type = InterfaceType.STREAM
        self.config = config
        self.text = ""
        self.decoder = opuslib_next.Decoder(16000, 1)
        self.asr_ws = None
        self.forward_task = None
        self.is_processing = False
        self.stop_sent = False
        self.finalizing = False
        self.remote_speech_ended = False
        self.session_completed = False

        self.api_key = str(config.get("api_key", "")).strip()
        if not self.api_key:
            raise ValueError("Gladia ASR requires api_key")

        self.base_url = str(config.get("base_url", "https://api.gladia.io")).rstrip("/")
        self.session_url = f"{self.base_url}/v2/live"
        self.output_dir = config.get("output_dir", "tmp/")
        self.delete_audio_file = delete_audio_file

        self.model_name = config.get("model_name", "solaria-1")
        self.encoding = config.get("encoding", "wav/pcm")
        self.bit_depth = int(config.get("bit_depth", 16))
        self.sample_rate = int(config.get("sample_rate", 16000))
        self.channels = int(config.get("channels", 1))
        self.endpointing = float(config.get("endpointing", 0.05))
        self.code_switching = self._to_bool(config.get("code_switching", False))
        self.receive_partial_transcripts = self._to_bool(
            config.get("receive_partial_transcripts", False)
        )

        raw_max_without_endpointing = float(
            config.get("maximum_duration_without_endpointing", 15)
        )
        # For voice-agent style usage, too-small values cause one utterance to be
        # cut into multiple finals, which in turn leads to repeated live session
        # creation. Keep a safer floor when partial transcripts are disabled.
        if not self.receive_partial_transcripts and raw_max_without_endpointing < 15:
            self.maximum_duration_without_endpointing = 15.0
        else:
            self.maximum_duration_without_endpointing = raw_max_without_endpointing

        self.language_list = self._normalize_languages(
            self._parse_languages(config.get("language", ""))
        )
        self.endpointing = self._normalize_endpointing(self.endpointing)
        self.audio_enhancer = self._to_bool(config.get("audio_enhancer", False))
        self.speech_threshold = float(config.get("speech_threshold", 0.6))
        logger.bind(tag=TAG).info(
            "Gladia normalized config: "
            f"languages={self.language_list or 'auto'}, "
            f"code_switching={self.code_switching}, "
            f"endpointing={self.endpointing}"
        )

    @staticmethod
    def _to_bool(value) -> bool:
        return str(value).lower() in ("1", "true", "yes", "on")

    @staticmethod
    def _parse_languages(value) -> List[str]:
        if isinstance(value, list):
            return [str(item).strip() for item in value if str(item).strip()]
        if value is None:
            return []
        normalized = str(value).replace(";", ",")
        return [item.strip() for item in normalized.split(",") if item.strip()]

    def _normalize_languages(self, languages: List[str]) -> List[str]:
        normalized = []
        for language in languages:
            item = language.strip().lower()
            if item and item not in normalized:
                normalized.append(item)

        if len(normalized) == 1:
            fallback_languages = SHORT_UTTERANCE_LANGUAGE_FALLBACKS.get(normalized[0], [])
            if fallback_languages:
                for language in fallback_languages:
                    if language not in normalized:
                        normalized.append(language)
                self.code_switching = True

        return normalized

    def _normalize_endpointing(self, endpointing: float) -> float:
        if not self.receive_partial_transcripts and endpointing < 0.2:
            return 0.2
        return endpointing

    async def receive_audio(self, conn: "ConnectionHandler", audio, audio_have_voice):
        await super().receive_audio(conn, audio, audio_have_voice)

        if (
            audio_have_voice
            and self.asr_ws is None
            and not self.is_processing
            and not self.finalizing
        ):
            await self._open_gladia_stream(conn)

        if self.asr_ws and self.is_processing and not self.stop_sent:
            try:
                pcm_frame = self.decoder.decode(audio, 960)
                await self.asr_ws.send(pcm_frame)
            except Exception as e:
                logger.bind(tag=TAG).error(f"Failed to send Gladia audio frame: {e}")

        if (
            self.asr_ws
            and self.is_processing
            and conn.client_voice_stop
            and len(conn.asr_audio) > 15
        ):
            await self._send_stop_request()

    async def _open_gladia_stream(self, conn: "ConnectionHandler"):
        self.is_processing = True
        self.stop_sent = False
        self.finalizing = False
        self.remote_speech_ended = False
        self.session_completed = False
        try:
            ws_url = await self._create_session(conn.session_id)
            logger.bind(tag=TAG).info(f"Connecting to Gladia live session: {ws_url}")
            self.asr_ws = await websockets.connect(
                ws_url,
                max_size=100000000,
                ping_interval=20,
                ping_timeout=20,
                close_timeout=10,
            )
            self.forward_task = asyncio.create_task(self._forward_asr_results(conn))

            # Replay only the preroll frames. The current frame will be sent by
            # receive_audio() immediately after the stream opens, so replaying it
            # here would duplicate it.
            preroll_audio = conn.asr_audio[:-1]
            if preroll_audio:
                for cached_audio in preroll_audio[-10:]:
                    try:
                        pcm_frame = self.decoder.decode(cached_audio, 960)
                        await self.asr_ws.send(pcm_frame)
                    except Exception as e:
                        logger.bind(tag=TAG).warning(
                            f"Failed to replay cached Gladia frame: {e}"
                        )
        except Exception as e:
            logger.bind(tag=TAG).error(f"Failed to establish Gladia ASR connection: {e}")
            if self.asr_ws:
                await self.asr_ws.close()
                self.asr_ws = None
            self.is_processing = False
            self.stop_sent = False
            self.finalizing = False

    async def _create_session(self, session_id: str) -> str:
        payload = {
            "encoding": self.encoding,
            "bit_depth": self.bit_depth,
            "sample_rate": self.sample_rate,
            "channels": self.channels,
            "model": self.model_name,
            "endpointing": self.endpointing,
            "maximum_duration_without_endpointing": self.maximum_duration_without_endpointing,
            "language_config": {
                "languages": self.language_list,
                "code_switching": self.code_switching,
            },
            "pre_processing": {
                "audio_enhancer": self.audio_enhancer,
                "speech_threshold": self.speech_threshold,
            },
            "realtime_processing": {
                "custom_vocabulary": False,
                "custom_spelling": False,
                "translation": False,
                "named_entity_recognition": False,
                "sentiment_analysis": False,
            },
            "messages_config": {
                "receive_partial_transcripts": self.receive_partial_transcripts,
                "receive_final_transcripts": True,
                "receive_speech_events": True,
                "receive_pre_processing_events": False,
                "receive_realtime_processing_events": False,
                "receive_post_processing_events": False,
                "receive_acknowledgments": True,
                "receive_errors": True,
                "receive_lifecycle_events": False,
            },
            "custom_metadata": {"session_id": session_id, "source": "xiaozhi"},
        }

        timeout = aiohttp.ClientTimeout(total=20)
        headers = {
            "X-Gladia-Key": self.api_key,
            "Content-Type": "application/json",
        }
        async with aiohttp.ClientSession(timeout=timeout) as session:
            async with session.post(
                self.session_url, headers=headers, json=payload
            ) as response:
                text = await response.text()
                if response.status >= 400:
                    raise RuntimeError(
                        f"Gladia session init failed: HTTP {response.status} {text}"
                    )
                data = json.loads(text)
                ws_url = data.get("url")
                if not ws_url:
                    raise RuntimeError(f"Gladia session init missing url: {text}")
                return ws_url

    async def _forward_asr_results(self, conn: "ConnectionHandler"):
        try:
            while self.asr_ws and not conn.stop_event.is_set():
                response = await self.asr_ws.recv()
                if isinstance(response, bytes):
                    continue
                try:
                    payload = json.loads(response)
                except json.JSONDecodeError:
                    logger.bind(tag=TAG).warning(f"Invalid Gladia payload: {response}")
                    continue

                event_type = payload.get("type") or payload.get("event")
                data = payload.get("data", {}) or {}

                if event_type == "error":
                    logger.bind(tag=TAG).error(f"Gladia ASR error: {payload}")
                    break

                if event_type == "speech_end":
                    self.remote_speech_ended = True
                    await self._maybe_finalize_turn(conn)
                    continue

                if event_type in ("post_final_transcript", "end_session"):
                    self.session_completed = True
                    transcript = self._extract_transcript_text(data)
                    if transcript:
                        self.text = transcript
                    await self._maybe_finalize_turn(conn)
                    continue

                if event_type == "transcript":
                    utterance = data.get("utterance", {}) or {}
                    transcript = str(utterance.get("text", "")).strip()
                    is_final = bool(data.get("is_final"))
                    language = utterance.get("language")

                    if transcript and (is_final or self.receive_partial_transcripts):
                        logger.bind(tag=TAG).info(f"Gladia transcript: {transcript}")
                        if language:
                            logger.bind(tag=TAG).info(f"Gladia language: {language}")

                    if is_final and transcript:
                        self.text = transcript
                        await self._maybe_finalize_turn(conn)

        except websockets.ConnectionClosed:
            logger.bind(tag=TAG).info("Gladia ASR connection closed")
        except Exception as e:
            logger.bind(tag=TAG).error(f"Gladia ASR receive loop failed: {e}")
        finally:
            if self.asr_ws:
                await self.asr_ws.close()
                self.asr_ws = None
            self.is_processing = False
            self.stop_sent = False
            self.finalizing = False
            self.remote_speech_ended = False
            self.session_completed = False
            conn.reset_audio_states()

    async def _maybe_finalize_turn(self, conn: "ConnectionHandler"):
        if self.finalizing:
            return

        if len(conn.asr_audio) <= 15:
            return

        should_finalize = False
        if conn.client_voice_stop and self.text:
            should_finalize = True
        elif self.stop_sent and (
            self.text or self.remote_speech_ended or self.session_completed
        ):
            should_finalize = True
        elif self.remote_speech_ended and conn.client_voice_stop:
            should_finalize = True

        if not should_finalize:
            return

        self.finalizing = True
        await self.handle_voice_stop(conn, conn.asr_audio.copy())

    @staticmethod
    def _extract_transcript_text(data) -> str:
        if isinstance(data, dict):
            if isinstance(data.get("utterance"), dict):
                text = data["utterance"].get("text")
                if text:
                    return text.strip()
            text = data.get("text")
            if text:
                return str(text).strip()
        return ""

    async def _send_stop_request(self):
        if not self.asr_ws or self.stop_sent:
            return
        self.stop_sent = True
        try:
            await self.asr_ws.send(json.dumps({"type": "stop_recording"}))
        except Exception as e:
            logger.bind(tag=TAG).warning(f"Failed to send Gladia stop request: {e}")
            self.stop_sent = False

    def stop_ws_connection(self):
        if self.asr_ws:
            asyncio.create_task(self.asr_ws.close())
            self.asr_ws = None
        self.is_processing = False
        self.stop_sent = False
        self.finalizing = False
        self.remote_speech_ended = False
        self.session_completed = False

    async def speech_to_text(self, opus_data, session_id, audio_format, artifacts=None):
        result = self.text
        self.text = ""
        return result, None

    async def close(self):
        if self.asr_ws:
            await self.asr_ws.close()
            self.asr_ws = None
        self.is_processing = False
        self.stop_sent = False
        self.finalizing = False
        self.remote_speech_ended = False
        self.session_completed = False
