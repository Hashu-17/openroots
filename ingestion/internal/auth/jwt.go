// Package auth verifies the access tokens issued by the Spring backend and checks
// project membership, so the Go telemetry APIs apply the same rules as Spring's REST API.
package auth

import (
	"crypto/hmac"
	"crypto/sha256"
	"crypto/sha512"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"hash"
	"strings"
	"time"
)

// ErrInvalidToken covers malformed, tampered, expired and wrongly signed tokens.
var ErrInvalidToken = errors.New("invalid access token")

const minimumSecretBytes = 32

// Verifier checks HMAC-signed JWTs created by Spring's TokenProvider (jjwt).
// jjwt picks HS256, HS384 or HS512 from the key length, so all three are accepted;
// any other algorithm (including "none") is rejected.
type Verifier struct {
	key []byte
	now func() time.Time
}

// Claims are the token fields the APIs use.
type Claims struct {
	Email     string
	ExpiresAt time.Time
}

// NewVerifier decodes the base64 secret shared with Spring (OPENROOTS_JWT_SECRET).
func NewVerifier(base64Secret string) (*Verifier, error) {
	key, err := base64.StdEncoding.DecodeString(strings.TrimSpace(base64Secret))
	if err != nil {
		return nil, fmt.Errorf("JWT secret must be base64: %w", err)
	}
	if len(key) < minimumSecretBytes {
		return nil, fmt.Errorf("JWT secret must decode to at least %d bytes", minimumSecretBytes)
	}
	return &Verifier{key: key, now: time.Now}, nil
}

// Verify checks the signature, algorithm and expiry, and returns the subject e-mail.
func (v *Verifier) Verify(token string) (Claims, error) {
	parts := strings.Split(token, ".")
	if len(parts) != 3 {
		return Claims{}, ErrInvalidToken
	}

	var header struct {
		Alg string `json:"alg"`
	}
	if err := decodeSegment(parts[0], &header); err != nil {
		return Claims{}, ErrInvalidToken
	}
	var newHash func() hash.Hash
	switch header.Alg {
	case "HS256":
		newHash = sha256.New
	case "HS384":
		newHash = sha512.New384
	case "HS512":
		newHash = sha512.New
	default:
		return Claims{}, ErrInvalidToken
	}

	signature, err := base64.RawURLEncoding.DecodeString(parts[2])
	if err != nil {
		return Claims{}, ErrInvalidToken
	}
	mac := hmac.New(newHash, v.key)
	mac.Write([]byte(parts[0] + "." + parts[1]))
	if !hmac.Equal(signature, mac.Sum(nil)) {
		return Claims{}, ErrInvalidToken
	}

	var payload struct {
		Subject   string   `json:"sub"`
		ExpiresAt *float64 `json:"exp"`
		NotBefore *float64 `json:"nbf"`
	}
	if err := decodeSegment(parts[1], &payload); err != nil {
		return Claims{}, ErrInvalidToken
	}
	now := v.now()
	if payload.ExpiresAt == nil || payload.Subject == "" {
		return Claims{}, ErrInvalidToken
	}
	expiresAt := time.Unix(int64(*payload.ExpiresAt), 0)
	if !now.Before(expiresAt) {
		return Claims{}, ErrInvalidToken
	}
	if payload.NotBefore != nil && now.Before(time.Unix(int64(*payload.NotBefore), 0)) {
		return Claims{}, ErrInvalidToken
	}
	return Claims{Email: payload.Subject, ExpiresAt: expiresAt}, nil
}

func decodeSegment(segment string, target any) error {
	data, err := base64.RawURLEncoding.DecodeString(segment)
	if err != nil {
		return err
	}
	return json.Unmarshal(data, target)
}
