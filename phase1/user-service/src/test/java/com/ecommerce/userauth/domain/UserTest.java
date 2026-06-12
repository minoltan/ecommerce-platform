package com.ecommerce.userauth.domain;

import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private static final String RAW_PASSWORD = "correct-horse-battery";

    private User registerUser() {
        return User.register(new Email("jane@example.com"), RAW_PASSWORD, "Jane Doe");
    }

    private User registerAndActivateUser() {
        User user = registerUser();
        user.verifyEmail();
        return user;
    }

    @Test
    void registerCreatesUnverifiedCustomer() {
        User user = registerUser();

        assertThat(user.getStatus()).isEqualTo(UserStatus.UNVERIFIED);
        assertThat(user.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(user.getEmail()).isEqualTo(new Email("jane@example.com"));
        assertThat(user.getEmailVerifiedAt()).isEmpty();
    }

    @Test
    void verifyEmailTransitionsUnverifiedToActive() {
        User user = registerUser();

        user.verifyEmail();

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getEmailVerifiedAt()).isPresent();
    }

    @Test
    void verifyEmailIsIdempotentWhenAlreadyActive() {
        User user = registerAndActivateUser();
        var firstVerifiedAt = user.getEmailVerifiedAt();

        user.verifyEmail();

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getEmailVerifiedAt()).isEqualTo(firstVerifiedAt);
    }

    @Test
    void verifyEmailThrowsWhenDeactivated() {
        User user = registerAndActivateUser();
        user.deactivate();

        assertThatThrownBy(user::verifyEmail)
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void loginSucceedsForActiveUserWithCorrectPassword() {
        User user = registerAndActivateUser();

        assertThatCode(() -> user.login(RAW_PASSWORD)).doesNotThrowAnyException();
    }

    @Test
    void loginThrowsInvalidCredentialsForWrongPassword() {
        User user = registerAndActivateUser();

        assertThatThrownBy(() -> user.login("wrong-password"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginThrowsIllegalStateTransitionWhenUnverified() {
        User user = registerUser();

        assertThatThrownBy(() -> user.login(RAW_PASSWORD))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void loginThrowsIllegalStateTransitionWhenDeactivated() {
        User user = registerAndActivateUser();
        user.deactivate();

        assertThatThrownBy(() -> user.login(RAW_PASSWORD))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void changePasswordUpdatesHashWhenCurrentPasswordCorrect() {
        User user = registerAndActivateUser();

        user.changePassword(RAW_PASSWORD, "new-correct-password");

        assertThatCode(() -> user.login("new-correct-password")).doesNotThrowAnyException();
        assertThatThrownBy(() -> user.login(RAW_PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void changePasswordThrowsInvalidCredentialsWhenCurrentPasswordWrong() {
        User user = registerAndActivateUser();

        assertThatThrownBy(() -> user.changePassword("wrong-current", "new-correct-password"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void changePasswordThrowsWhenDeactivated() {
        User user = registerAndActivateUser();
        user.deactivate();

        assertThatThrownBy(() -> user.changePassword(RAW_PASSWORD, "new-correct-password"))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void resetPasswordReplacesHash() {
        User user = registerAndActivateUser();

        user.resetPassword("reset-correct-password");

        assertThatCode(() -> user.login("reset-correct-password")).doesNotThrowAnyException();
    }

    @Test
    void resetPasswordThrowsWhenDeactivated() {
        User user = registerAndActivateUser();
        user.deactivate();

        assertThatThrownBy(() -> user.resetPassword("reset-correct-password"))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void deactivateTransitionsActiveToDeactivated() {
        User user = registerAndActivateUser();

        user.deactivate();

        assertThat(user.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
        assertThat(user.getDeactivatedAt()).isPresent();
    }

    @Test
    void deactivateTransitionsUnverifiedToDeactivated() {
        User user = registerUser();

        user.deactivate();

        assertThat(user.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
    }

    @Test
    void deactivateThrowsWhenAlreadyDeactivated() {
        User user = registerAndActivateUser();
        user.deactivate();

        assertThatThrownBy(user::deactivate)
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void setDefaultAddressEnforcesSingleDefaultPerUser() {
        User user = registerAndActivateUser();
        UserAddress home = new UserAddress("1 Home St", null, "Chennai", "TN", "600001", "IN");
        UserAddress work = new UserAddress("2 Work Ave", null, "Chennai", "TN", "600002", "IN");
        user.addAddress(home);
        user.addAddress(work);

        user.setDefaultAddress(home.getId());
        assertThat(home.isDefault()).isTrue();
        assertThat(work.isDefault()).isFalse();

        user.setDefaultAddress(work.getId());
        assertThat(home.isDefault()).isFalse();
        assertThat(work.isDefault()).isTrue();
    }

    @Test
    void setDefaultAddressThrowsForUnknownAddressId() {
        User user = registerAndActivateUser();

        assertThatThrownBy(() -> user.setDefaultAddress(UUID.randomUUID()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void removeAddressMarksAddressDeleted() {
        User user = registerAndActivateUser();
        UserAddress address = new UserAddress("1 Home St", null, "Chennai", "TN", "600001", "IN");
        user.addAddress(address);

        user.removeAddress(address.getId());

        assertThat(address.getDeletedAt()).isNotNull();
    }
}
