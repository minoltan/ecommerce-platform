# Use Case Diagram — User / Auth

```mermaid
graph TD
    Guest(["👤 Guest"])
    Customer(["👤 Customer"])
    Admin(["👤 Admin"])

    subgraph UC_Auth ["User / Auth Bounded Context"]
        UC1["Register Account"]
        UC2["Verify Email"]
        UC3["Login"]
        UC4["Refresh Token"]
        UC5["Logout"]
        UC6["Reset Password"]
        UC7["Update Profile"]
        UC8["Manage Addresses"]
        UC9["Deactivate User Account"]
    end

    EmailProvider(["📧 Email Provider"])

    Guest --> UC1
    Guest --> UC3

    UC1 -.->|includes| UC2
    UC2 -.->|triggers| EmailProvider
    UC6 -.->|triggers| EmailProvider

    Customer --> UC3
    Customer --> UC4
    Customer --> UC5
    Customer --> UC6
    Customer --> UC7
    Customer --> UC8

    Admin --> UC9
    Admin --> UC3
```

## Use Case Descriptions

| ID | Use Case | Primary Actor | Precondition | Postcondition |
|---|---|---|---|---|
| UC-UA-01 | Register Account | Guest | Email not already registered | Account in UNVERIFIED state; verification email sent |
| UC-UA-02 | Verify Email | Guest | Registration complete; token valid | Account ACTIVE; user logged in |
| UC-UA-03 | Login | Guest / Customer | Account ACTIVE | JWT + refresh token issued |
| UC-UA-04 | Refresh Token | Customer | Valid refresh token | New access token issued |
| UC-UA-05 | Logout | Customer | Authenticated | Refresh token invalidated |
| UC-UA-06 | Reset Password | Guest / Customer | Account exists | Password updated; all sessions invalidated |
| UC-UA-07 | Update Profile | Customer | Authenticated | Profile updated |
| UC-UA-08 | Manage Addresses | Customer | Authenticated | Delivery addresses saved |
| UC-UA-09 | Deactivate User | Admin | Target account exists | Account DEACTIVATED; all tokens revoked |
