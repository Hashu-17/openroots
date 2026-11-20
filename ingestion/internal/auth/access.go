package auth

import (
	"context"
	"errors"
	"net/http"
	"strings"
)

// ErrNotVisible means the device does not exist or the user is not a member of its
// project. Both cases look the same to the caller (BR-010), as in the Spring API.
var ErrNotVisible = errors.New("device not found")

// MembershipChecker reports whether a user (by e-mail) is a member of a project.
type MembershipChecker interface {
	IsProjectMember(ctx context.Context, email, projectID string) (bool, error)
}

// DeviceProjects resolves a device to its project from the cached registry.
type DeviceProjects interface {
	ProjectOf(deviceID string) (string, bool)
}

// Authorizer applies Spring's telemetry read rule: any project member may read.
type Authorizer struct {
	Verifier *Verifier
	Members  MembershipChecker
	Devices  DeviceProjects
}

// AuthorizeRead verifies the token and the caller's read access to the device.
// It returns ErrInvalidToken, ErrNotVisible, or a storage error.
func (a *Authorizer) AuthorizeRead(ctx context.Context, token, deviceID string) (Claims, error) {
	claims, err := a.Verifier.Verify(token)
	if err != nil {
		return Claims{}, ErrInvalidToken
	}
	projectID, exists := a.Devices.ProjectOf(deviceID)
	if !exists {
		return Claims{}, ErrNotVisible
	}
	member, err := a.Members.IsProjectMember(ctx, claims.Email, projectID)
	if err != nil {
		return Claims{}, err
	}
	if !member {
		return Claims{}, ErrNotVisible
	}
	return claims, nil
}

// BearerToken extracts the token from an "Authorization: Bearer <token>" header.
func BearerToken(request *http.Request) string {
	header := request.Header.Get("Authorization")
	if !strings.HasPrefix(header, "Bearer ") {
		return ""
	}
	return strings.TrimSpace(strings.TrimPrefix(header, "Bearer "))
}
