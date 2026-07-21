package com.example.hello;

import com.example.crypt.CryptoSample;
import java.security.GeneralSecurityException;

public class App {
    public String getGreeting() {
        return "Hello World!";
    }

    public static void main(String[] args) throws GeneralSecurityException {
        System.out.println(new App().getGreeting());
        System.out.println();
        CryptoSample.runDemo();
    }
}
