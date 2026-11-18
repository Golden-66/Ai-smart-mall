package com.aimall.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * 首次启动时生成演示数据: 200 款商品 / 约 535 个 SKU (手机、笔记本、耳机、平板、手表)。
 * 品牌均为虚构, 使用固定随机种子, 每次生成结果一致 (型号 PH-1001 / LT-1001 / HP-1001 ...)。
 * 商品库非空时不会重复生成。
 */
@Component
@ConditionalOnProperty(name = "mall.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final String[] BRANDS = {"星河", "极光", "云帆", "澄境", "曜石", "凌云", "墨竹"};
    private static final String[] COLORS = {"曜石黑", "月光银", "海岛蓝", "珊瑚粉", "松石绿"};

    private final ProductRepository repo;
    private final ObjectMapper mapper;
    private final Random rnd = new Random(20260101L);
    private int skuCount = 0;

    public DataSeeder(ProductRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (repo.countAllProducts() > 0) {
            return;
        }
        log.info("商品库为空, 开始生成演示数据 ...");
        for (int i = 0; i < 60; i++) seedPhone(i);
        for (int i = 0; i < 50; i++) seedLaptop(i);
        for (int i = 0; i < 40; i++) seedHeadphone(i);
        for (int i = 0; i < 25; i++) seedTablet(i);
        for (int i = 0; i < 25; i++) seedWatch(i);
        log.info("演示数据生成完成: 200 款商品, {} 个 SKU", skuCount);
    }

    // ---------------------------------------------------------------- 手机
    private void seedPhone(int i) throws Exception {
        String brand = pick(BRANDS);
        String series = pick("X", "Neo", "Ultra", "Note", "Lite");
        int gen = 8 + rnd.nextInt(6);
        int tier = rnd.nextInt(4); // 0入门 .. 3旗舰
        String name = brand + " " + series + gen + (tier == 3 ? " Pro" : "");
        String tags = switch (series) {
            case "Ultra" -> "拍照,商务,旗舰";
            case "Neo" -> "游戏,性能,续航";
            case "Note" -> "续航,学生,性价比";
            case "Lite" -> "学生,性价比,轻薄";
            default -> "拍照,游戏,商务";
        };
        Map<String, Object> specs = new LinkedHashMap<>();
        specs.put("处理器", pick("8核 旗舰移动平台", "8核 高性能移动平台", "8核 均衡移动平台"));
        specs.put("屏幕", pick("6.1英寸 OLED 120Hz", "6.7英寸 OLED 120Hz", "6.8英寸 LTPO 144Hz"));
        specs.put("电池", (4200 + rnd.nextInt(1800)) + "mAh");
        specs.put("后置摄像头", pick("5000万像素主摄", "5000万像素主摄+长焦", "2亿像素主摄"));
        specs.put("快充", pick("66W", "80W", "120W"));
        long base = 1499L + tier * 1800L + rnd.nextInt(5) * 100L;
        long pid = repo.insertProduct(name, brand, "手机", String.format("PH-%d", 1001 + i), tags,
                brand + " " + series + gen + " 是一款面向" + tags.replace(",", "、") + "用户的智能手机。"
                        + specs.get("屏幕") + "，" + specs.get("电池") + "电池，支持" + specs.get("快充") + "快充。",
                mapper.writeValueAsString(specs));
        int[][] configs = {{8, 128}, {12, 256}, {16, 512}};
        long[] extra = {0, 500, 1300};
        for (int k = 0; k < 3; k++) {
            sku(pid, pick(COLORS), configs[k][0], configs[k][1], base + extra[k]);
        }
    }

    // ---------------------------------------------------------------- 笔记本
    private void seedLaptop(int i) throws Exception {
        String brand = pick(BRANDS);
        String series = pick("Book Air", "Book Pro", "Game Pro", "Studio", "Pad Max");
        int gen = 12 + rnd.nextInt(4);
        String name = brand + " " + series + " " + gen;
        boolean gaming = series.equals("Game Pro");
        boolean studio = series.equals("Studio");
        String tags = gaming ? "游戏,高性能,电竞" : studio ? "设计,剪辑,高性能,编程"
                : series.equals("Book Air") ? "轻薄,办公,学生,通勤" : "办公,编程,商务";
        Map<String, Object> specs = new LinkedHashMap<>();
        specs.put("处理器", pick("8核 低功耗处理器", "12核 标压处理器", "16核 旗舰处理器"));
        specs.put("显卡", gaming || studio ? pick("独立显卡 6GB", "独立显卡 8GB") : "集成显卡");
        specs.put("屏幕", pick("14英寸 2.8K 120Hz", "15.6英寸 FHD 144Hz", "16英寸 3.2K 120Hz"));
        specs.put("重量", gaming ? "2.3kg" : series.equals("Book Air") ? "1.2kg" : "1.6kg");
        specs.put("续航", gaming ? "6小时" : "12小时");
        long base = gaming ? 6999L : studio ? 7499L : series.equals("Book Air") ? 3999L : 4999L;
        base += rnd.nextInt(6) * 100L + (gen - 12) * 300L;
        long pid = repo.insertProduct(name, brand, "笔记本", String.format("LT-%d", 1001 + i), tags,
                brand + " " + series + " " + gen + " 笔记本电脑，适合" + tags.replace(",", "、") + "场景。"
                        + specs.get("处理器") + "，" + specs.get("显卡") + "，" + specs.get("屏幕") + "。",
                mapper.writeValueAsString(specs));
        int[][] configs = {{8, 256}, {16, 512}, {32, 1024}};
        long[] extra = {0, 700, 2000};
        String color = pick("银色", "深空灰");
        for (int k = 0; k < 3; k++) {
            sku(pid, color, configs[k][0], configs[k][1], base + extra[k]);
        }
    }

    // ---------------------------------------------------------------- 耳机
    private void seedHeadphone(int i) throws Exception {
        String brand = pick(BRANDS);
        String series = pick("Buds", "Pods", "Over-Ear", "Sport");
        int gen = 2 + rnd.nextInt(5);
        String name = brand + " " + series + " " + gen;
        boolean overEar = series.equals("Over-Ear");
        String tags = overEar ? "降噪,音乐,影音,通勤" : series.equals("Sport") ? "运动,防水,通勤" : "降噪,通勤,游戏";
        Map<String, Object> specs = new LinkedHashMap<>();
        specs.put("佩戴方式", overEar ? "头戴式" : series.equals("Sport") ? "挂耳式" : "入耳式");
        specs.put("降噪", series.equals("Sport") ? "无" : pick("主动降噪", "混合降噪"));
        specs.put("续航", (6 + rnd.nextInt(30)) + "小时");
        specs.put("蓝牙", pick("5.3", "5.4"));
        long base = overEar ? 799L : series.equals("Sport") ? 299L : 399L;
        base += rnd.nextInt(8) * 50L;
        long pid = repo.insertProduct(name, brand, "耳机", String.format("HP-%d", 1001 + i), tags,
                brand + " " + series + " " + gen + " 无线耳机，" + specs.get("佩戴方式") + "，" + specs.get("降噪")
                        + "，续航" + specs.get("续航") + "。",
                mapper.writeValueAsString(specs));
        sku(pid, "曜石黑", null, null, base);
        sku(pid, "月光银", null, null, base + 20);
    }

    // ---------------------------------------------------------------- 平板
    private void seedTablet(int i) throws Exception {
        String brand = pick(BRANDS);
        String series = pick("Pad", "Tab Pro", "Tab Lite");
        int gen = 5 + rnd.nextInt(5);
        String name = brand + " " + series + " " + gen;
        String tags = series.equals("Tab Pro") ? "绘画,办公,影音,设计" : series.equals("Tab Lite") ? "学习,影音,性价比" : "学习,影音,办公";
        Map<String, Object> specs = new LinkedHashMap<>();
        specs.put("屏幕", pick("11英寸 2.5K 120Hz", "12.4英寸 3K 144Hz", "10.9英寸 FHD 90Hz"));
        specs.put("处理器", pick("8核 均衡平台", "8核 高性能平台"));
        specs.put("电池", (7000 + rnd.nextInt(4000)) + "mAh");
        specs.put("手写笔", series.equals("Tab Pro") ? "支持, 4096级压感" : "选配");
        long base = series.equals("Tab Pro") ? 3499L : series.equals("Tab Lite") ? 1299L : 1999L;
        base += rnd.nextInt(5) * 100L;
        long pid = repo.insertProduct(name, brand, "平板", String.format("TB-%d", 1001 + i), tags,
                brand + " " + series + " " + gen + " 平板电脑，" + specs.get("屏幕") + "，适合" + tags.replace(",", "、") + "。",
                mapper.writeValueAsString(specs));
        int[][] configs = {{6, 128}, {8, 256}, {12, 512}};
        long[] extra = {0, 400, 1100};
        String color = pick("深空灰", "星光银");
        for (int k = 0; k < 3; k++) {
            sku(pid, color, configs[k][0], configs[k][1], base + extra[k]);
        }
    }

    // ---------------------------------------------------------------- 手表
    private void seedWatch(int i) throws Exception {
        String brand = pick(BRANDS);
        String series = pick("Watch", "Band", "Watch GT");
        int gen = 3 + rnd.nextInt(6);
        String name = brand + " " + series + " " + gen;
        String tags = series.equals("Band") ? "运动,健康,性价比" : series.equals("Watch GT") ? "运动,户外,续航" : "健康,商务,通勤";
        Map<String, Object> specs = new LinkedHashMap<>();
        specs.put("屏幕", pick("1.43英寸 AMOLED", "1.32英寸 AMOLED", "1.5英寸 LTPO"));
        specs.put("续航", (3 + rnd.nextInt(18)) + "天");
        specs.put("防水", pick("5ATM", "10ATM"));
        specs.put("传感器", "心率、血氧、GPS");
        long base = series.equals("Band") ? 199L : series.equals("Watch GT") ? 1299L : 899L;
        base += rnd.nextInt(6) * 50L;
        long pid = repo.insertProduct(name, brand, "智能手表", String.format("WT-%d", 1001 + i), tags,
                brand + " " + series + " " + gen + " 智能手表，" + specs.get("屏幕") + "，续航" + specs.get("续航") + "，支持"
                        + specs.get("传感器") + "监测。",
                mapper.writeValueAsString(specs));
        sku(pid, "曜石黑 / 42mm", null, null, base);
        sku(pid, "月光银 / 46mm", null, null, base + 100);
    }

    // ---------------------------------------------------------------- helpers
    private void sku(long productId, String color, Integer ram, Integer storage, long price) {
        String skuName = ram == null ? color : color + " / " + ram + "G+" + storage + "G";
        repo.insertSku(productId, skuName, color, ram, storage, BigDecimal.valueOf(price), 30 + rnd.nextInt(170));
        skuCount++;
    }

    private String pick(String... options) {
        return options[rnd.nextInt(options.length)];
    }
}
