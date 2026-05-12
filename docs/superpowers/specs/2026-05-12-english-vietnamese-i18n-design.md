# English and Vietnamese I18n Design

## Scope

Add English and Vietnamese localization to the current React frontend and Spring Boot backend API messages.

The app will support switching between `en` and `vi`, persist the selected language in the browser, localize visible frontend text, and send the selected language to the backend with `Accept-Language`. The backend will keep the current API response shape and return localized `message` values for success and error responses.

## Frontend Architecture

- Add a small app-owned i18n module with typed dictionaries for `en` and `vi`.
- Add an `I18nProvider` and `useI18n()` hook for `t(key, params?)`, current language, and language changes.
- Store the selected language in `localStorage`; default to browser language when it starts with `vi`, otherwise use `en`.
- Configure Ant Design locale from the selected app language.
- Add a compact language switcher near the app brand so auth and dashboard pages can be switched without routing changes.

## Frontend Coverage

Translate hardcoded strings in:

- Auth page alerts, request fallback errors, and resend OTP success text.
- Auth form labels, validation messages, placeholders, segmented controls, card title, and buttons.
- Verify email card labels, validation messages, cooldown button text, and navigation buttons.
- Dashboard title, session card labels, signed-in tag, boolean values, and sign-out button.
- Shared HTTP fallback errors.

API error messages remain rendered from the backend response, but the frontend sends `Accept-Language` so those messages match the selected language.

## Backend Architecture

- Add Spring message bundles:
  - `messages.properties` for English defaults.
  - `messages_vi.properties` for Vietnamese translations.
- Configure message resolution through Spring Boot defaults and set fallback language to English.
- Replace controller success message literals with message keys.
- Replace auth exception message literals with localized message keys and optional arguments.
- Update `GlobalExceptionHandler` to resolve localized messages from exceptions and validation failures.

## Backend Message Contract

The response wrapper stays unchanged:

```json
{
  "success": true,
  "message": "Signin successful",
  "data": {},
  "timestamp": "..."
}
```

The `message` value is localized using the request locale. For example, `Accept-Language: vi` returns Vietnamese text. Missing or unsupported languages return English.

Cooldown throttling keeps `Retry-After` unchanged and localizes only the JSON `message`, using the retry seconds as a message argument.

## Error Handling

Frontend validation errors are localized in the frontend dictionaries.

Backend business and validation errors are localized in `GlobalExceptionHandler`. Validation errors can continue returning a single general "Invalid request" message for now; field-level error detail is out of scope.

Network or malformed response failures use localized frontend fallback text.

## Testing

Frontend:

- Add focused tests only if a test framework already exists; otherwise rely on TypeScript build and lint.
- Verify `npm run build` from `CentralAuth-fe`.

Backend:

- Add or update integration tests to prove English default messages still work.
- Add Vietnamese `Accept-Language: vi` assertions for representative success and error responses.
- Include the resend cooldown error to verify argument interpolation and `Retry-After`.
- Verify `mvn test` from `CentralAuth-be`.

## Out Of Scope

- Translating email templates; the current OTP is logged, not sent through a user-facing email template.
- Localizing database content or user-entered display names.
- Adding route prefixes such as `/vi/signin`.
- Returning structured error codes in the API response.
