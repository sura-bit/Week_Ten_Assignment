package org.example.weektenassignment;


public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
