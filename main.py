import argparse
import json
import re
import sys
import unicodedata
from difflib import SequenceMatcher
from pathlib import Path


DATA_DIR = Path(__file__).resolve().parent / "data"


def normalize(text):
    text = unicodedata.normalize("NFD", text.lower().replace("đ", "d"))
    text = "".join(char for char in text if not unicodedata.combining(char))
    return " ".join(re.sub(r"[^a-z0-9]+", " ", text).split())


def contains(text, phrase):
    return f" {phrase} " in f" {text} "


class FabricChatbot:
    def __init__(self, data_dir=DATA_DIR):
        with (data_dir / "intents.json").open(encoding="utf-8-sig") as file:
            self.intents = json.load(file)["intents"]
        with (data_dir / "fabrics.json").open(encoding="utf-8-sig") as file:
            self.fabrics = json.load(file)
        if not self.intents or not self.fabrics:
            raise ValueError("Dữ liệu câu hỏi hoặc vải đang trống.")
        for item in self.intents:
            if not isinstance(item["examples"], list) or not item["examples"]:
                raise ValueError("Mỗi intent cần có danh sách examples không rỗng.")
            if not all(isinstance(example, str) for example in item["examples"]):
                raise ValueError("Các câu hỏi mẫu phải là chuỗi văn bản.")
            if item["intent"] not in {"ask_price", "ask_color", "ask_stock"}:
                raise ValueError(f"Intent chưa được hỗ trợ: {item['intent']}")
        for fabric in self.fabrics.values():
            for field in ("name", "price", "colors", "stock"):
                if field not in fabric:
                    raise ValueError(f"Dữ liệu vải thiếu trường {field}.")
            if not isinstance(fabric["colors"], list):
                raise ValueError("Trường colors phải là một danh sách.")
        self.aliases = {
            key: {normalize(key), normalize(fabric["name"])}
            for key, fabric in self.fabrics.items()
        }

    def question_pattern(self, text):
        # Thay tên vải để câu hỏi mẫu cotton cũng dùng được cho linen.
        aliases = set().union(*self.aliases.values())
        for alias in sorted(aliases, key=len, reverse=True):
            text = re.sub(r"\b" + re.escape(alias) + r"\b", "fabric", text)
        return text

    def find_intents(self, text):
        cues = {
            "ask_price": ("gia", "bao nhieu tien", "bao nhieu mot met"),
            "ask_color": ("mau",),
            "ask_stock": ("con hang", "ton kho", "trong kho", "kho con", "con bao nhieu"),
        }
        available = {item["intent"] for item in self.intents}
        matches = [
            intent for intent, phrases in cues.items()
            if intent in available and any(contains(text, phrase) for phrase in phrases)
        ]
        if matches:
            return matches
        pattern = self.question_pattern(text)
        scored = [
            (SequenceMatcher(None, pattern, self.question_pattern(normalize(example))).ratio(), item["intent"])
            for item in self.intents for example in item["examples"]
        ]
        score, intent = max(scored)
        return [intent] if score >= 0.8 else []

    def answer(self, question):
        text = normalize(question)
        if not text:
            return "Bạn hãy nhập một câu hỏi về vải."
        keys = [
            key for key, aliases in self.aliases.items()
            if any(contains(text, alias) for alias in aliases)
        ]
        if not keys:
            names = ", ".join(fabric["name"] for fabric in self.fabrics.values())
            return f"Bạn muốn hỏi loại vải nào? Dữ liệu hiện có: {names}."
        intents = self.find_intents(text)
        if not intents:
            return "Tôi có thể tra giá, màu sắc và tồn kho. Ví dụ: 'Giá vải cotton bao nhiêu?'"

        responses = []
        for key in keys:
            fabric = self.fabrics[key]
            name = fabric["name"]
            for intent in intents:
                if intent == "ask_price":
                    price = f"{fabric['price']:,.0f}".replace(",", ".")
                    responses.append(f"Giá {name}: {price} đồng.")
                elif intent == "ask_color":
                    colors = ", ".join(fabric["colors"])
                    # Luôn liệt kê màu đã lưu, không suy đoán màu ngoài dữ liệu.
                    responses.append(f"Các màu có trong dữ liệu của {name}: {colors or 'chưa có thông tin'}.")
                elif intent == "ask_stock":
                    if fabric["stock"] > 0:
                        responses.append(f"{name} còn hàng. Số lượng tồn kho: {fabric['stock']}.")
                    else:
                        responses.append(f"{name} hiện hết hàng.")
        return "\n".join(responses)


def main():
    # Giữ nguyên tiếng Việt cả trên terminal Windows và khi chuyển hướng output.
    for stream in (sys.stdin, sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description="Test hỏi đáp về vải từ hai file JSON.")
    parser.add_argument("question", nargs="*", help="Câu hỏi; bỏ trống để chat liên tục.")
    args = parser.parse_args()
    try:
        bot = FabricChatbot()
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(f"Không thể đọc dữ liệu trong {DATA_DIR}: {error}", file=sys.stderr)
        return 1
    if args.question:
        print(bot.answer(" ".join(args.question)))
        return 0

    print("Tra cứu vải — nhập câu hỏi về giá, màu sắc hoặc tồn kho.")
    print("Gõ 'thoát', 'exit' hoặc 'quit' để kết thúc.")
    print("Lưu ý: dữ liệu hiện chưa khai báo đơn vị tính giá và tồn kho.")
    while True:
        try:
            question = input("Bạn: ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\nĐã kết thúc.")
            break
        if normalize(question) in {"thoat", "exit", "quit"}:
            print("Đã kết thúc.")
            break
        print(f"Bot: {bot.answer(question)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
