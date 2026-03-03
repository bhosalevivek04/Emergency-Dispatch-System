package com.vivek.auth.controller;

import com.vivek.auth.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.PublicKey;
import java.util.Base64;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class PublicKeyController {

    private final JwtService jwtService;

    @GetMapping("/public-key")
    public PublicKeyResponse getPublicKey() {
        PublicKey publicKey = jwtService.getPublicKey();
        String encodedKey = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        
        return new PublicKeyResponse(
                encodedKey,
                publicKey.getAlgorithm(),
                publicKey.getFormat()
        );
    }

    record PublicKeyResponse(
            String key,
            String algorithm,
            String format
    ) {}
}
