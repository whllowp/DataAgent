package com.dataagent.service;

import com.dataagent.config.DataAgentProperties;
import com.dataagent.web.dto.SqlDtos;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class PiiService {

    private final DataAgentProperties properties;

    public PiiService(DataAgentProperties properties) {
        this.properties = properties;
    }

    public String classify(String columnName, String label) {
        if (!properties.getPii().isEnabled()) {
            return null;
        }
        String value = ((columnName == null ? "" : columnName) + " "
                + (label == null ? "" : label)).toLowerCase(Locale.ROOT);
        if (value.matches(".*(email|e-mail|mail_addr).*")) {
            return "EMAIL";
        }
        if (value.matches(".*(phone|mobile|telephone|tel_no).*")) {
            return "PHONE";
        }
        if (value.matches(".*(id_card|idcard|identity_no|passport|ssn|social_security).*")) {
            return "ID_CARD";
        }
        if (value.matches(".*(bank_card|card_no|account_no|iban).*")) {
            return "BANK_CARD";
        }
        if (value.matches(".*(customer_name|customer_full_name|contact_name|user_name|full_name).*")) {
            return "PERSON_NAME";
        }
        if (value.matches(".*(address|street|postal_code|zip_code).*")) {
            return "ADDRESS";
        }
        return null;
    }

    public Masked mask(List<SqlDtos.ColumnMeta> columns, List<List<Object>> rows) {
        if (!properties.getPii().isEnabled() || !properties.getPii().isMaskEnabled()) {
            return new Masked(rows, List.of());
        }
        List<String> masked = new ArrayList<>();
        int[] types = new int[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            String pii = classify(columns.get(i).name(), columns.get(i).label());
            if (pii != null) {
                masked.add(columns.get(i).label());
                types[i] = index(pii);
            }
        }
        if (masked.isEmpty()) {
            return new Masked(rows, List.of());
        }
        List<List<Object>> result = new ArrayList<>(rows.size());
        for (List<Object> row : rows) {
            List<Object> copy = new ArrayList<>(row);
            for (int i = 0; i < copy.size() && i < types.length; i++) {
                if (types[i] > 0) {
                    copy.set(i, mask(types[i], copy.get(i)));
                }
            }
            result.add(copy);
        }
        return new Masked(result, masked);
    }

    public String mask(String type, Object value) {
        return mask(index(type), value);
    }

    private String mask(int type, Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return switch (type) {
            case 1 -> maskEmail(text);
            case 2 -> maskPhone(text);
            case 3 -> maskIdCard(text);
            case 4 -> maskBankCard(text);
            case 5 -> maskName(text);
            case 6 -> maskAddress(text);
            default -> "***";
        };
    }

    private static int index(String type) {
        if (type == null) {
            return 0;
        }
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "EMAIL" -> 1;
            case "PHONE" -> 2;
            case "ID_CARD" -> 3;
            case "BANK_CARD" -> 4;
            case "PERSON_NAME" -> 5;
            case "ADDRESS" -> 6;
            default -> 0;
        };
    }

    private static String maskEmail(String value) {
        int at = value.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return value.charAt(0) + "***" + value.substring(at);
    }

    private static String maskPhone(String value) {
        String digits = value.replaceAll("\\D", "");
        if (digits.length() < 7) {
            return "***";
        }
        return digits.substring(0, 3) + "****" + digits.substring(digits.length() - 4);
    }

    private static String maskIdCard(String value) {
        if (value.length() < 8) {
            return "***";
        }
        return value.substring(0, 3) + "********" + value.substring(value.length() - 4);
    }

    private static String maskBankCard(String value) {
        String digits = value.replaceAll("\\s", "");
        if (digits.length() < 4) {
            return "***";
        }
        return "**** **** **** " + digits.substring(digits.length() - 4);
    }

    private static String maskName(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return value.charAt(0) + "**";
    }

    private static String maskAddress(String value) {
        return value.length() <= 6 ? "***" : value.substring(0, 6) + "***";
    }

    public record Masked(List<List<Object>> rows, List<String> columns) {
    }
}
