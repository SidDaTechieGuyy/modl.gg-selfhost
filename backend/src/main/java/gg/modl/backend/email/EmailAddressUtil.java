package gg.modl.backend.email;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import java.util.Locale;

public final class EmailAddressUtil {
    private static final int MAX_EMAIL_LENGTH = 254;
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private record Address(@Email String value) {}

    private EmailAddressUtil() {
    }

    public static String normalizeIfValid(String email) {
        String normalized = normalize(email);
        return isValid(normalized) ? normalized : null;
    }

    public static String normalize(String email) {
        if (email == null) {
            return null;
        }

        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    public static boolean isValid(String email) {
        String normalized = normalize(email);
        if (normalized == null || normalized.length() > MAX_EMAIL_LENGTH) {
            return false;
        }

        return VALIDATOR.validate(new Address(normalized)).isEmpty();
    }

    public static String mask(String email) {
        String normalized = normalize(email);
        if (normalized == null) {
            return "<empty>";
        }

        int atIndex = normalized.indexOf('@');
        if (atIndex <= 0) {
            return "***";
        }

        return normalized.charAt(0) + "***" + normalized.substring(atIndex);
    }
}
