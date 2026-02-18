package bogget.studycraft;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Map;

public class StudycraftNetworking {

    // --- Payload Records ---

    public record OpenQuizPayload(String question, String answer1, String answer2, String answer3, String answer4, int correctIndex) implements CustomPayload {
        public static final Id<OpenQuizPayload> ID = new Id<>(Identifier.of(Studycraft.MOD_ID, "open_quiz"));
        public static final PacketCodec<PacketByteBuf, OpenQuizPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, OpenQuizPayload::question,
            PacketCodecs.STRING, OpenQuizPayload::answer1,
            PacketCodecs.STRING, OpenQuizPayload::answer2,
            PacketCodecs.STRING, OpenQuizPayload::answer3,
            PacketCodecs.STRING, OpenQuizPayload::answer4,
            PacketCodecs.VAR_INT, OpenQuizPayload::correctIndex,
            OpenQuizPayload::new
        );
        @Override public Id<OpenQuizPayload> getId() { return ID; }
    }

    public record SubmitAnswerPayload(int result, String question, String correctAnswer) implements CustomPayload {
        public static final Id<SubmitAnswerPayload> ID = new Id<>(Identifier.of(Studycraft.MOD_ID, "submit_answer"));
        public static final PacketCodec<PacketByteBuf, SubmitAnswerPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, SubmitAnswerPayload::result,
            PacketCodecs.STRING, SubmitAnswerPayload::question,
            PacketCodecs.STRING, SubmitAnswerPayload::correctAnswer,
            SubmitAnswerPayload::new
        );
        @Override public Id<SubmitAnswerPayload> getId() { return ID; }
    }

    public record UpdateQuestionBankPayload(String content) implements CustomPayload {
        public static final Id<UpdateQuestionBankPayload> ID = new Id<>(Identifier.of(Studycraft.MOD_ID, "update_question_bank"));
        public static final PacketCodec<PacketByteBuf, UpdateQuestionBankPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, UpdateQuestionBankPayload::content,
            UpdateQuestionBankPayload::new
        );
        @Override public Id<UpdateQuestionBankPayload> getId() { return ID; }
    }

    public record RequestStatsPayload() implements CustomPayload {
        public static final Id<RequestStatsPayload> ID = new Id<>(Identifier.of(Studycraft.MOD_ID, "request_stats"));
        public static final PacketCodec<PacketByteBuf, RequestStatsPayload> CODEC = PacketCodec.unit(new RequestStatsPayload());
        @Override public Id<RequestStatsPayload> getId() { return ID; }
    }

    public record GiveItemPayload() implements CustomPayload {
        public static final Id<GiveItemPayload> ID = new Id<>(Identifier.of(Studycraft.MOD_ID, "give_item"));
        public static final PacketCodec<PacketByteBuf, GiveItemPayload> CODEC = PacketCodec.unit(new GiveItemPayload());
        @Override public Id<GiveItemPayload> getId() { return ID; }
    }

    public record DifficultyUpdatePayload(int hungerInterval, int hungerGain) implements CustomPayload {
        public static final Id<DifficultyUpdatePayload> ID = new Id<>(Identifier.of(Studycraft.MOD_ID, "difficulty_update"));
        public static final PacketCodec<PacketByteBuf, DifficultyUpdatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, DifficultyUpdatePayload::hungerInterval,
            PacketCodecs.VAR_INT, DifficultyUpdatePayload::hungerGain,
            DifficultyUpdatePayload::new
        );
        @Override public Id<DifficultyUpdatePayload> getId() { return ID; }
    }

    public record StatsDataPayload(String statsJson, float overallPercent) implements CustomPayload {
        public static final Id<StatsDataPayload> ID = new Id<>(Identifier.of(Studycraft.MOD_ID, "stats_data"));
        public static final PacketCodec<PacketByteBuf, StatsDataPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, StatsDataPayload::statsJson,
            PacketCodecs.FLOAT, StatsDataPayload::overallPercent,
            StatsDataPayload::new
        );
        @Override public Id<StatsDataPayload> getId() { return ID; }
    }

    // --- Registration ---

    public static void registerPayloads() {
        // Server-bound (client → server)
        PayloadTypeRegistry.playC2S().register(SubmitAnswerPayload.ID, SubmitAnswerPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateQuestionBankPayload.ID, UpdateQuestionBankPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestStatsPayload.ID, RequestStatsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(GiveItemPayload.ID, GiveItemPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DifficultyUpdatePayload.ID, DifficultyUpdatePayload.CODEC);

        // Client-bound (server → client)
        PayloadTypeRegistry.playS2C().register(OpenQuizPayload.ID, OpenQuizPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StatsDataPayload.ID, StatsDataPayload.CODEC);
    }

    public static void registerHandlers() {
        ServerPlayNetworking.registerGlobalReceiver(SubmitAnswerPayload.ID, (payload, context) -> {
            context.server().execute(() -> handleSubmitAnswer(payload, context.player()));
        });
        ServerPlayNetworking.registerGlobalReceiver(UpdateQuestionBankPayload.ID, (payload, context) -> {
            context.server().execute(() -> Studycraft.updateQuestionBank(payload.content()));
        });
        ServerPlayNetworking.registerGlobalReceiver(RequestStatsPayload.ID, (payload, context) -> {
            context.server().execute(() -> handleRequestStats(context.player()));
        });
        ServerPlayNetworking.registerGlobalReceiver(GiveItemPayload.ID, (payload, context) -> {
            context.server().execute(() -> handleGiveItem(context.player()));
        });
        ServerPlayNetworking.registerGlobalReceiver(DifficultyUpdatePayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                Studycraft.setServerHungerInterval(payload.hungerInterval());
                Studycraft.setServerHungerGain(payload.hungerGain());
            });
        });
    }

    public static void registerClientHandlers() {
        ClientPlayNetworking.registerGlobalReceiver(OpenQuizPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                QuestionBank.QuizData quizData = new QuestionBank.QuizData(
                    payload.question(),
                    payload.answer1(),
                    java.util.List.of(payload.answer1(), payload.answer2(), payload.answer3(), payload.answer4()),
                    payload.correctIndex()
                );
                MinecraftClient.getInstance().setScreen(new QuizScreen(quizData));
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(StatsDataPayload.ID, (payload, context) -> {
            context.client().execute(() -> handleStatsData(payload));
        });
    }

    // --- Server-side packet sending ---

    public static void sendOpenQuizPacket(ServerPlayerEntity player) {
        QuestionBank.QuizData quizData = Studycraft.getQuestionBank().getRandomQuestion();
        if (quizData == null) return;
        java.util.List<String> answers = quizData.getAllAnswers();
        ServerPlayNetworking.send(player, new OpenQuizPayload(
            quizData.getQuestion(),
            answers.get(0), answers.get(1), answers.get(2), answers.get(3),
            quizData.getCorrectIndex()
        ));
    }

    // --- Client-side packet sending ---

    public static void sendAnswerPacket(int result, String question, String correctAnswer) {
        ClientPlayNetworking.send(new SubmitAnswerPayload(result, question, correctAnswer));
    }

    public static void sendUpdateQuestionBankPacket(String content) {
        ClientPlayNetworking.send(new UpdateQuestionBankPayload(content));
    }

    public static void requestStats() {
        ClientPlayNetworking.send(new RequestStatsPayload());
    }

    public static void sendGiveItemPacket() {
        ClientPlayNetworking.send(new GiveItemPayload());
    }

    public static void sendDifficultyUpdatePacket(int hungerInterval, int hungerGain) {
        ClientPlayNetworking.send(new DifficultyUpdatePayload(hungerInterval, hungerGain));
    }

    // --- Handlers ---

    private static void handleSubmitAnswer(SubmitAnswerPayload payload, ServerPlayerEntity player) {
        boolean isCorrect = payload.result() == 0;
        QuizStatistics stats = Studycraft.getQuizStatistics();
        if (stats != null) {
            stats.recordAnswer(player.getUuid(), payload.question(), isCorrect);
        }
        if (isCorrect) {
            int gain = Studycraft.getServerHungerGain();
            player.getHungerManager().add(gain, 0.5f);
            player.sendMessage(Text.literal("§a[StudyCraft]§r Correct! +" + (gain / 2.0) + " drumsticks"), true);
        } else {
            player.sendMessage(Text.literal("§c[StudyCraft]§r Wrong! The answer was: " + payload.correctAnswer()), true);
        }
    }

    private static void handleRequestStats(ServerPlayerEntity player) {
        QuizStatistics stats = Studycraft.getQuizStatistics();
        if (stats == null) return;
        Map<String, QuizStatistics.StatsEntry> allStats = stats.getAllStats(player.getUuid());
        float overallPercent = stats.getOverallPercentCorrect(player.getUuid());

        // Serialize stats to JSON string simply
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, QuizStatistics.StatsEntry> entry : allStats.entrySet()) {
            if (!first) json.append(",");
            String escapedKey = entry.getKey().replace("\"", "\\\"");
            QuizStatistics.StatsEntry s = entry.getValue();
            json.append("\"").append(escapedKey).append("\":")
                .append("{\"correct\":").append(s.getTimesCorrect())
                .append(",\"wrong\":").append(s.getTimesWrong()).append("}");
            first = false;
        }
        json.append("}");

        ServerPlayNetworking.send(player, new StatsDataPayload(json.toString(), overallPercent));
    }

    private static void handleGiveItem(ServerPlayerEntity player) {
        ItemStack quizCard = new ItemStack(Studycraft.QUIZ_ITEM);
        quizCard.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Quiz Card"));
        player.getInventory().insertStack(quizCard);
    }

    private static void handleStatsData(StatsDataPayload payload) {
        // Parse the simple JSON and update client stats
        // This is a simple parser for our specific format
        Map<String, QuizStatistics.StatsEntry> statsMap = new java.util.HashMap<>();
        try {
            String json = payload.statsJson().trim();
            if (json.equals("{}")) {
                Studycraft.getClientStats().updateStats(statsMap, payload.overallPercent());
                return;
            }
            // Remove outer braces
            json = json.substring(1, json.length() - 1);
            // Split by "}," to get individual entries
            String[] entries = json.split("\\},");
            for (String entry : entries) {
                entry = entry.trim().replace("}", "");
                int colonIdx = entry.indexOf("\":{");
                if (colonIdx < 0) continue;
                String key = entry.substring(1, colonIdx).replace("\\\"", "\"");
                String values = entry.substring(colonIdx + 3);
                int correct = 0, wrong = 0;
                for (String part : values.split(",")) {
                    if (part.contains("\"correct\":")) correct = Integer.parseInt(part.split(":")[1].trim());
                    if (part.contains("\"wrong\":")) wrong = Integer.parseInt(part.split(":")[1].trim());
                }
                statsMap.put(key, new QuizStatistics.StatsEntry(correct, wrong));
            }
        } catch (Exception e) {
            Studycraft.LOGGER.error("Failed to parse stats JSON", e);
        }
        Studycraft.getClientStats().updateStats(statsMap, payload.overallPercent());

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof StudycraftConfigScreen screen) {
            screen.onStatsReceived();
        }
    }
}