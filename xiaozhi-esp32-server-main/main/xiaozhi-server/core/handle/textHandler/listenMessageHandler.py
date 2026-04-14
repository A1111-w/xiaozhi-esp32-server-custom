import time
import asyncio
from typing import Dict, Any, TYPE_CHECKING

if TYPE_CHECKING:
    from core.connection import ConnectionHandler

from core.handle.abortHandle import handleAbortMessage
from core.handle.receiveAudioHandle import startToChat
from core.handle.reportHandle import enqueue_asr_report
from core.handle.sendAudioHandle import send_stt_message, send_tts_message
from core.handle.textMessageHandler import TextMessageHandler
from core.handle.textMessageType import TextMessageType
from core.utils.util import remove_punctuation_and_length
from core.providers.asr.dto.dto import InterfaceType

TAG = __name__
DEFAULT_INTERRUPT_WORDS = ["停一下", "别说了", "小智闭嘴"]


def is_interrupt_word(filtered_text: str, interrupt_words) -> bool:
    if not filtered_text or not interrupt_words:
        return False

    for word in interrupt_words:
        if not word:
            continue
        _, normalized_word = remove_punctuation_and_length(str(word))
        if normalized_word and filtered_text == normalized_word:
            return True
    return False


class ListenTextMessageHandler(TextMessageHandler):
    """Handle incoming listen messages."""

    @property
    def message_type(self) -> TextMessageType:
        return TextMessageType.LISTEN

    async def handle(self, conn: "ConnectionHandler", msg_json: Dict[str, Any]) -> None:
        if "mode" in msg_json:
            conn.client_listen_mode = msg_json["mode"]
            conn.logger.bind(tag=TAG).debug(
                f"客户端收听模式: {conn.client_listen_mode}"
            )

        if msg_json["state"] == "start":
            conn.reset_audio_states()
        elif msg_json["state"] == "stop":
            conn.client_voice_stop = True
            if conn.asr.interface_type == InterfaceType.STREAM:
                asyncio.create_task(conn.asr._send_stop_request())
            elif len(conn.asr_audio) > 0:
                asr_audio_task = conn.asr_audio.copy()
                conn.reset_audio_states()
                if len(asr_audio_task) > 0:
                    await conn.asr.handle_voice_stop(conn, asr_audio_task)
        elif msg_json["state"] == "detect":
            conn.client_have_voice = False
            conn.reset_audio_states()
            if "text" not in msg_json:
                return

            conn.last_activity_time = time.time() * 1000
            original_text = msg_json["text"]
            _, filtered_text = remove_punctuation_and_length(original_text)

            is_wakeup_words = filtered_text in conn.config.get("wakeup_words")
            enable_greeting = conn.config.get("enable_greeting", True)

            interrupt_cfg = conn.config.get("interrupt", {}) or {}
            interrupt_enabled = interrupt_cfg.get("enabled", True)
            interrupt_words = interrupt_cfg.get("words", []) or DEFAULT_INTERRUPT_WORDS
            allow_wakeup_words = interrupt_cfg.get("allow_wakeup_words", True)
            is_interrupt_words = interrupt_enabled and is_interrupt_word(
                filtered_text, interrupt_words
            )

            # While the assistant is speaking, only configured interrupt words
            # or wake words are allowed to stop playback.
            if conn.client_is_speaking:
                if is_interrupt_words or (
                    interrupt_enabled and allow_wakeup_words and is_wakeup_words
                ):
                    await send_stt_message(conn, original_text)
                    await handleAbortMessage(conn)
                return

            if is_wakeup_words and not enable_greeting:
                await send_stt_message(conn, original_text)
                await send_tts_message(conn, "stop", None)
                conn.client_is_speaking = False
            elif is_wakeup_words:
                conn.just_woken_up = True
                enqueue_asr_report(conn, "嗯，你好呀", [])
                await startToChat(conn, "嗯，你好呀")
            else:
                conn.just_woken_up = True
                enqueue_asr_report(conn, original_text, [])
                await startToChat(conn, original_text)
