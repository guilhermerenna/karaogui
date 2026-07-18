package com.karaogui.backend.game;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class JoinCodeGenerator {

    private static final char[] LETTERS = "ABCDEFGHJKMNPQRSTVWXYZ".toCharArray(); // excl I,L,O,U
    private static final char[] DIGITS = "23456789".toCharArray();                 // excl 0,1
    private static final SecureRandom RANDOM = new SecureRandom();

    public String generate() {
        char[] code = new char[6];
        // pattern: letter-digit-letter-digit-letter-digit (positions 0,2,4 = letter; 1,3,5 = digit)
        code[0] = LETTERS[RANDOM.nextInt(LETTERS.length)];
        code[1] = DIGITS[RANDOM.nextInt(DIGITS.length)];
        code[2] = LETTERS[RANDOM.nextInt(LETTERS.length)];
        code[3] = DIGITS[RANDOM.nextInt(DIGITS.length)];
        code[4] = LETTERS[RANDOM.nextInt(LETTERS.length)];
        code[5] = DIGITS[RANDOM.nextInt(DIGITS.length)];
        return new String(code);
    }

    public static String toDisplayFormat(String code) {
        return code.substring(0, 3) + " " + code.substring(3);
    }

    public static String normalize(String input) {
        return input.replace(" ", "").toUpperCase();
    }
}
