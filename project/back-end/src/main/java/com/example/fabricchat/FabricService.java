package com.example.fabricchat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class FabricService {
    public record Fabric(String name, BigDecimal price, List<String> colors, BigDecimal width, Integer stock) {}
    public record Intent(String intent, List<String> examples) {}
    public record IntentData(List<Intent> intents) {}

    private final Map<String, Fabric> fabrics;
    private final List<Intent> intents;
    private static final Map<String, List<String>> CUES = Map.of(
        "ask_price", List.of("gia", "bao nhieu tien", "bao nhieu mot met"),
        "ask_color", List.of("mau"),
        "ask_stock", List.of("con hang", "ton kho", "trong kho", "kho con", "con bao nhieu")
    );

    public FabricService(ObjectMapper mapper, @Value("${fabric.data-dir:}") String dataDir) throws IOException {
        try (InputStream input = open(dataDir, "fabrics.json")) {
            fabrics = mapper.readValue(input, new TypeReference<>() {});
        }
        try (InputStream input = open(dataDir, "intents.json")) {
            intents = mapper.readValue(input, IntentData.class).intents();
        }
        if (fabrics == null || fabrics.isEmpty() || intents == null || intents.isEmpty()) {
            throw new IllegalArgumentException("Dữ liệu vải hoặc câu hỏi đang trống.");
        }
        for (Fabric fabric : fabrics.values()) {
            if (fabric == null || fabric.name() == null || fabric.name().isBlank()
                || fabric.price() == null || fabric.price().signum() < 0 || fabric.colors() == null
                || fabric.colors().stream().anyMatch(color -> color == null || color.isBlank())
                || fabric.stock() == null || fabric.stock() < 0) {
                throw new IllegalArgumentException("Dữ liệu vải cần có name, price, colors và stock hợp lệ.");
            }
        }
        for (Intent intent : intents) {
            if (intent == null || intent.intent() == null || !CUES.containsKey(intent.intent())
                || intent.examples() == null || intent.examples().isEmpty()
                || intent.examples().stream().anyMatch(example -> example == null || example.isBlank())) {
                throw new IllegalArgumentException("Intent cần có tên được hỗ trợ và danh sách examples hợp lệ.");
            }
        }
    }

    private InputStream open(String dataDir, String filename) throws IOException {
        return dataDir.isBlank() ? new ClassPathResource("data/" + filename).getInputStream()
            : Files.newInputStream(Path.of(dataDir).resolve(filename));
    }

    public Map<String, Fabric> getFabrics() {
        return fabrics;
    }

    static String normalize(String value) {
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").replaceAll("[^a-z0-9]+", " ").trim();
    }

    private boolean contains(String text, String phrase) {
        return (" " + text + " ").contains(" " + phrase + " ");
    }

    private String questionPattern(String text) {
        List<String> aliases = new ArrayList<>();
        fabrics.forEach((key, fabric) -> {
            aliases.add(normalize(key));
            aliases.add(normalize(fabric.name()));
        });
        aliases.sort(Comparator.comparingInt(String::length).reversed());
        for (String alias : aliases) {
            text = text.replaceAll("\\b" + Pattern.quote(alias) + "\\b", "fabric");
        }
        return text;
    }

    public String answer(String question) {
        String text = normalize(question);
        List<Fabric> selected = fabrics.entrySet().stream()
            .filter(entry -> contains(text, normalize(entry.getKey())) || contains(text, normalize(entry.getValue().name())))
            .map(Map.Entry::getValue).toList();
        if (selected.isEmpty()) {
            return "Bạn muốn hỏi loại vải nào? Hiện có: "
                + String.join(", ", fabrics.values().stream().map(Fabric::name).toList()) + ".";
        }
        Set<String> matches = new LinkedHashSet<>();
        String pattern = questionPattern(text);
        for (Intent intent : intents) {
            boolean exampleMatches = intent.examples().stream()
                .anyMatch(example -> questionPattern(normalize(example)).equals(pattern));
            boolean cueMatches = CUES.get(intent.intent()).stream().anyMatch(cue -> contains(text, cue));
            if (exampleMatches || cueMatches) {
                matches.add(intent.intent());
            }
        }
        if (matches.isEmpty()) {
            return "Tôi có thể tra giá, màu sắc và tồn kho. Bạn thử hỏi: Giá vải cotton bao nhiêu?";
        }
        List<String> answers = new ArrayList<>();
        NumberFormat money = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
        for (Fabric fabric : selected) {
            for (String intent : matches) {
                answers.add(switch (intent) {
                    case "ask_price" -> "Giá " + fabric.name() + ": " + money.format(fabric.price()) + " đồng.";
                    case "ask_color" -> "Các màu có trong dữ liệu của " + fabric.name() + ": "
                        + (fabric.colors().isEmpty() ? "chưa có thông tin" : String.join(", ", fabric.colors())) + ".";
                    case "ask_stock" -> fabric.stock() > 0
                        ? fabric.name() + " còn hàng. Số lượng tồn kho: " + fabric.stock() + "."
                        : fabric.name() + " hiện hết hàng.";
                    default -> throw new IllegalStateException("Intent chưa hỗ trợ: " + intent);
                });
            }
        }
        return String.join("\n", answers);
    }
}
