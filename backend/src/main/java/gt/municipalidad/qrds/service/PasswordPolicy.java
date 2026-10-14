package gt.municipalidad.qrds.service;

import java.security.SecureRandom;

public final class PasswordPolicy {

    public static final String MENSAJE =
            "La contraseña debe tener al menos 8 caracteres, una mayúscula, una minúscula y un dígito.";

    private static final String MAYUSCULAS = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String MINUSCULAS = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITOS = "23456789";
    private static final String TODO = MAYUSCULAS + MINUSCULAS + DIGITOS;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private PasswordPolicy() {
    }

    public static boolean cumple(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        boolean mayuscula = false;
        boolean minuscula = false;
        boolean digito = false;
        for (int i = 0; i < password.length(); i++) {
            char caracter = password.charAt(i);
            if (Character.isUpperCase(caracter)) {
                mayuscula = true;
            } else if (Character.isLowerCase(caracter)) {
                minuscula = true;
            } else if (Character.isDigit(caracter)) {
                digito = true;
            }
        }
        return mayuscula && minuscula && digito;
    }

    public static String generar() {
        char[] valor = new char[12];
        valor[0] = MAYUSCULAS.charAt(ALEATORIO.nextInt(MAYUSCULAS.length()));
        valor[1] = MINUSCULAS.charAt(ALEATORIO.nextInt(MINUSCULAS.length()));
        valor[2] = DIGITOS.charAt(ALEATORIO.nextInt(DIGITOS.length()));
        for (int i = 3; i < valor.length; i++) {
            valor[i] = TODO.charAt(ALEATORIO.nextInt(TODO.length()));
        }
        for (int i = valor.length - 1; i > 0; i--) {
            int destino = ALEATORIO.nextInt(i + 1);
            char temporal = valor[i];
            valor[i] = valor[destino];
            valor[destino] = temporal;
        }
        return new String(valor);
    }
}
