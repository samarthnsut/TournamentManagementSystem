package com.acme.tms.identity.api;

import com.acme.tms.identity.dto.ChangePasswordRequest;
import com.acme.tms.identity.dto.ProfileResponse;
import com.acme.tms.identity.dto.UpdateProfileRequest;
import com.acme.tms.identity.service.ProfileService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Your own account.
 *
 * <p>No {@code @RequiresPermission} anywhere here, and that is the point: the subject is always the
 * authenticated caller, resolved server-side from the token. There is no id in any path or body, so
 * there is nothing to swap for someone else's.
 */
@RestController
@RequestMapping("/api/v1/users/me")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ProfileResponse get() {
        return profileService.get();
    }

    @PatchMapping
    public ProfileResponse update(@Valid @RequestBody UpdateProfileRequest request) {
        return profileService.update(request);
    }

    /** 204 rather than a body: every session just ended, including this one. */
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        profileService.changePassword(request);
    }
}
