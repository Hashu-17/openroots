package auth

import (
	"crypto/hmac"
	"crypto/sha256"
	"crypto/sha512"
	"encoding/base64"
	"encoding/json"
	"hash"
	"testing"
	"time"
)

var secret = base64.StdEncoding.EncodeToString([]byte("openroots-go-test-secret-that-is-48-bytes-long!!"))

func sign(t *testing.T, alg string, newHash func() hash.Hash, key []byte, claims map[string]any) string {
	t.Helper()
	header, _ := json.Marshal(map[string]string{"alg": alg})
	payload, _ := json.Marshal(claims)
	unsigned := base64.RawURLEncoding.EncodeToString(header) + "." + base64.RawURLEncoding.EncodeToString(payload)
	mac := hmac.New(newHash, key)
	mac.Write([]byte(unsigned))
	return unsigned + "." + base64.RawURLEncoding.EncodeToString(mac.Sum(nil))
}

func verifier(t *testing.T) (*Verifier, []byte) {
	t.Helper()
	v, err := NewVerifier(secret)
	if err != nil {
		t.Fatal(err)
	}
	v.now = func() time.Time { return time.Unix(1_000_000, 0) }
	return v, v.key
}

func TestAcceptsEveryHmacAlgorithmJjwtCanChoose(t *testing.T) {
	v, key := verifier(t)
	claims := map[string]any{"sub": "member@example.com", "iat": 999_000, "exp": 1_000_900}
	for alg, newHash := range map[string]func() hash.Hash{"HS256": sha256.New, "HS384": sha512.New384, "HS512": sha512.New} {
		got, err := v.Verify(sign(t, alg, newHash, key, claims))
		if err != nil {
			t.Fatalf("%s: %v", alg, err)
		}
		if got.Email != "member@example.com" || !got.ExpiresAt.Equal(time.Unix(1_000_900, 0)) {
			t.Fatalf("%s: unexpected claims %+v", alg, got)
		}
	}
}

func TestRejectsInvalidTokens(t *testing.T) {
	v, key := verifier(t)
	valid := map[string]any{"sub": "member@example.com", "exp": 1_000_900}
	cases := map[string]string{
		"expired":       sign(t, "HS256", sha256.New, key, map[string]any{"sub": "a@b.c", "exp": 1_000_000}),
		"no expiry":     sign(t, "HS256", sha256.New, key, map[string]any{"sub": "a@b.c"}),
		"no subject":    sign(t, "HS256", sha256.New, key, map[string]any{"exp": 1_000_900}),
		"other key":     sign(t, "HS256", sha256.New, []byte("another-key-that-is-at-least-thirty-two-bytes"), valid),
		"alg none":      sign(t, "none", sha256.New, key, valid),
		"not yet valid": sign(t, "HS256", sha256.New, key, map[string]any{"sub": "a@b.c", "exp": 1_000_900, "nbf": 1_000_100}),
		"garbage":       "not.a.jwt",
		"too few parts": "abc.def",
	}
	for name, token := range cases {
		if _, err := v.Verify(token); err == nil {
			t.Errorf("%s: expected rejection", name)
		}
	}
	tampered := sign(t, "HS256", sha256.New, key, valid)
	tampered = tampered[:len(tampered)-2] + "AA"
	if _, err := v.Verify(tampered); err == nil {
		t.Error("tampered signature: expected rejection")
	}
}

func TestRejectsShortOrNonBase64Secrets(t *testing.T) {
	if _, err := NewVerifier(base64.StdEncoding.EncodeToString([]byte("short"))); err == nil {
		t.Error("expected short secret to be rejected")
	}
	if _, err := NewVerifier("%%%"); err == nil {
		t.Error("expected non-base64 secret to be rejected")
	}
}
