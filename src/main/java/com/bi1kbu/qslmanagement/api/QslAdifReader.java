package com.bi1kbu.qslmanagement.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 服务端 ADIF 读取器。
 *
 * <p>行为基准是控制台前端的既有实现（{@code ui/src/views/modules/QsoRecordModule.vue} 的
 * {@code parseAdifRecords} / {@code parseAdifFieldMap} / {@code parseAdifDate} /
 * {@code parseAdifTime}）。外部日志同步与手工导入必须算出同一套字段与同一套判重键，
 * 因此这里刻意保持与前端一致的细节：字段名统一大写、长度前缀按字符数截取、
 * 同一字段重复出现时后者覆盖前者、日期时间不合法时返回空串交给调用方判定整行失败。
 *
 * <p>与前端唯一的有意差异：记录切分在扫描过程中完成，长度前缀值内部出现的
 * {@code <EOR>} 文本不会被误当成记录边界（前端先 split 再解析，遇到这种值会切错）。
 * 对合法 ADIF 两者结果相同，对本读取器更稳。
 */
public final class QslAdifReader {

    private static final String END_OF_HEADER = "EOH";
    private static final String END_OF_RECORD = "EOR";
    private static final Pattern TAG_NAME = Pattern.compile("[A-Z0-9_]+");
    private static final Pattern TAG_LENGTH = Pattern.compile("\\d+");
    private static final Pattern ADIF_DATE = Pattern.compile("^(\\d{4})(\\d{2})(\\d{2})$");
    private static final Pattern ADIF_TIME = Pattern.compile("^(\\d{2})(\\d{2})(?:\\d{2})?$");

    private QslAdifReader() {
    }

    /**
     * 解析整份 ADIF 文本，返回逐条记录（键为大写字段名，值为已 trim 的原文）。
     *
     * <p>头部字段（{@code <EOH>} 之前）不进入结果；缺 {@code <EOH>} 时整份文本都视为记录区。
     */
    public static List<Map<String, String>> parseRecords(String content) {
        List<Map<String, String>> records = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return records;
        }
        String text = stripBom(content);
        boolean hasHeader = indexOfTagIgnoreCase(text, END_OF_HEADER) >= 0;
        boolean inHeader = hasHeader;
        Map<String, String> current = new LinkedHashMap<>();
        int position = 0;
        int length = text.length();
        while (position < length) {
            int open = text.indexOf('<', position);
            if (open < 0) {
                break;
            }
            int close = text.indexOf('>', open);
            if (close < 0) {
                break;
            }
            String tag = text.substring(open + 1, close);
            if (END_OF_HEADER.equalsIgnoreCase(tag)) {
                inHeader = false;
                current = new LinkedHashMap<>();
                position = close + 1;
                continue;
            }
            if (END_OF_RECORD.equalsIgnoreCase(tag)) {
                if (!inHeader && !current.isEmpty()) {
                    records.add(current);
                }
                current = new LinkedHashMap<>();
                position = close + 1;
                continue;
            }
            int firstColon = tag.indexOf(':');
            if (firstColon < 0) {
                // ADIF 里无冒号的标记不带值，跳过即可。
                position = close + 1;
                continue;
            }
            String name = tag.substring(0, firstColon).trim().toUpperCase(Locale.ROOT);
            String lengthText = tag.substring(firstColon + 1);
            int secondColon = lengthText.indexOf(':');
            if (secondColon >= 0) {
                lengthText = lengthText.substring(0, secondColon);
            }
            lengthText = lengthText.trim();
            if (!TAG_NAME.matcher(name).matches() || !TAG_LENGTH.matcher(lengthText).matches()) {
                position = close + 1;
                continue;
            }
            int fieldLength = Integer.parseInt(lengthText);
            int valueEnd = Math.min(length, close + 1 + fieldLength);
            String value = text.substring(close + 1, valueEnd).trim();
            if (!inHeader) {
                // 与前端一致：同一字段重复出现时后值覆盖前值。
                current.put(name, value);
            }
            position = valueEnd;
        }
        if (!inHeader && !current.isEmpty()) {
            // 结尾缺 <EOR> 的残尾记录仍然收下，交给上层按字段完整性判定。
            records.add(current);
        }
        return records;
    }

    /**
     * 解析 {@code <EOH>} 之前的头部字段。
     *
     * <p>用途是取数据源自带的增量游标：ARRL LoTW 在头部返回 {@code APP_LoTW_LASTQSORX}
     * （官方文档明确建议用它作为下一次查询的 {@code qso_qsorxsince}）。没有
     * {@code <EOH>} 时返回空表，调用方据此回退到按记录内容推算游标。</p>
     */
    public static Map<String, String> parseHeader(String content) {
        Map<String, String> header = new LinkedHashMap<>();
        if (content == null || content.isEmpty()) {
            return header;
        }
        String text = stripBom(content);
        int headerEnd = indexOfTagIgnoreCase(text, END_OF_HEADER);
        if (headerEnd < 0) {
            return header;
        }
        int position = 0;
        while (position < headerEnd) {
            int open = text.indexOf('<', position);
            if (open < 0 || open >= headerEnd) {
                break;
            }
            int close = text.indexOf('>', open);
            if (close < 0 || close >= headerEnd) {
                break;
            }
            String tag = text.substring(open + 1, close);
            int firstColon = tag.indexOf(':');
            if (firstColon < 0) {
                position = close + 1;
                continue;
            }
            String name = tag.substring(0, firstColon).trim().toUpperCase(Locale.ROOT);
            String lengthText = tag.substring(firstColon + 1);
            int secondColon = lengthText.indexOf(':');
            if (secondColon >= 0) {
                lengthText = lengthText.substring(0, secondColon);
            }
            lengthText = lengthText.trim();
            if (!TAG_NAME.matcher(name).matches() || !TAG_LENGTH.matcher(lengthText).matches()) {
                position = close + 1;
                continue;
            }
            int fieldLength = Integer.parseInt(lengthText);
            int valueEnd = Math.min(headerEnd, close + 1 + fieldLength);
            header.put(name, text.substring(close + 1, valueEnd).trim());
            position = valueEnd;
        }
        return header;
    }

    /** 解析单条记录文本（用于测试与增量片段）。 */
    public static Map<String, String> parseRecord(String recordText) {
        List<Map<String, String>> records = parseRecords(recordText);
        return records.isEmpty() ? new LinkedHashMap<>() : records.get(0);
    }

    /** {@code YYYYMMDD} → {@code YYYY-MM-DD}；不合法返回空串。 */
    public static String parseAdifDate(String value) {
        String normalized = value == null ? "" : value.trim();
        Matcher matcher = ADIF_DATE.matcher(normalized);
        if (!matcher.matches()) {
            return "";
        }
        return matcher.group(1) + "-" + matcher.group(2) + "-" + matcher.group(3);
    }

    /** {@code HHMM[SS]} → {@code HHMM}；不合法返回空串。 */
    public static String parseAdifTime(String value) {
        String normalized = value == null ? "" : value.trim();
        Matcher matcher = ADIF_TIME.matcher(normalized);
        if (!matcher.matches()) {
            return "";
        }
        return matcher.group(1) + matcher.group(2);
    }

    private static String stripBom(String content) {
        return content.startsWith("\uFEFF") ? content.substring(1) : content;
    }

    private static int indexOfTagIgnoreCase(String text, String tagName) {
        String needle = "<" + tagName + ">";
        return text.toUpperCase(Locale.ROOT).indexOf(needle);
    }
}
