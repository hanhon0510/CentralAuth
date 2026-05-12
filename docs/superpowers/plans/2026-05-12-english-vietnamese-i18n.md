# English Vietnamese I18n Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add English and Vietnamese localization to the React UI and Spring Boot API response messages.

**Architecture:** The frontend owns typed UI dictionaries, persists the selected language, applies Ant Design locale, and sends `Accept-Language` on every API request. The backend uses Spring `MessageSource` with English defaults and Vietnamese translations, while keeping the current `ApiResponse` wrapper unchanged.

**Tech Stack:** Java 21, Spring Boot 3.5.x, JUnit/MockMvc, React 19, TypeScript, Ant Design 6, Vite.

---

## File Map

- Modify `CentralAuth-be/src/test/java/com/centralauth/auth/AuthControllerIntegrationTests.java`: add Vietnamese `Accept-Language` integration tests and keep English default assertions.
- Create `CentralAuth-be/src/main/java/com/centralauth/common/LocalizedApiException.java`: base runtime exception carrying a message key and interpolation args.
- Create `CentralAuth-be/src/main/java/com/centralauth/common/Messages.java`: small Spring component that resolves message keys for the current request locale.
- Modify `CentralAuth-be/src/main/java/com/centralauth/auth/DuplicateEmailException.java`: switch from hardcoded English to message key.
- Modify `CentralAuth-be/src/main/java/com/centralauth/auth/InvalidCredentialsException.java`: switch from hardcoded English to message key.
- Modify `CentralAuth-be/src/main/java/com/centralauth/auth/InvalidEmailVerificationOtpException.java`: switch from hardcoded English to message key.
- Modify `CentralAuth-be/src/main/java/com/centralauth/auth/EmailVerificationNotPendingException.java`: switch from hardcoded English to message key.
- Modify `CentralAuth-be/src/main/java/com/centralauth/auth/EmailVerificationOtpResendThrottledException.java`: switch from hardcoded English to message key with retry seconds arg.
- Modify `CentralAuth-be/src/main/java/com/centralauth/auth/AuthController.java`: resolve success messages through `Messages`.
- Modify `CentralAuth-be/src/main/java/com/centralauth/common/GlobalExceptionHandler.java`: resolve localized exception and validation messages through `Messages`.
- Modify `CentralAuth-be/src/main/resources/application.yml`: configure Spring message bundle settings.
- Create `CentralAuth-be/src/main/resources/messages.properties`: English backend messages.
- Create `CentralAuth-be/src/main/resources/messages_vi.properties`: Vietnamese backend messages.
- Create `CentralAuth-fe/src/shared/i18n/messages.ts`: frontend typed English and Vietnamese dictionaries plus formatter.
- Create `CentralAuth-fe/src/shared/i18n/language.ts`: language storage and browser-language resolution.
- Create `CentralAuth-fe/src/shared/i18n/I18nContext.tsx`: provider with `t()`, current language, and language setter.
- Create `CentralAuth-fe/src/shared/i18n/useI18n.ts`: hook wrapper around the context.
- Create `CentralAuth-fe/src/shared/i18n/LanguageSwitcher.tsx`: compact EN/VI segmented control.
- Modify `CentralAuth-fe/src/app/providers/AppProviders.tsx`: wrap the app in `I18nProvider` and apply Ant Design locale.
- Modify `CentralAuth-fe/src/shared/lib/http.ts`: add `Accept-Language` and localized fallback request errors.
- Modify `CentralAuth-fe/src/features/auth/pages/AuthPage.tsx`: translate auth page alerts and fallback errors, render language switcher.
- Modify `CentralAuth-fe/src/features/auth/components/AuthFormCard.tsx`: translate auth form UI and validation messages.
- Modify `CentralAuth-fe/src/features/auth/components/VerifyEmailCard.tsx`: translate verification UI, validation messages, and cooldown label.
- Modify `CentralAuth-fe/src/features/dashboard/pages/DashboardPage.tsx`: translate dashboard title and render language switcher.
- Modify `CentralAuth-fe/src/features/auth/components/SessionCard.tsx`: translate session summary UI.

---

### Task 1: Backend Locale Contract Tests

**Files:**
- Modify: `CentralAuth-be/src/test/java/com/centralauth/auth/AuthControllerIntegrationTests.java`

- [ ] **Step 1: Write failing Vietnamese success test**

Add this test method:

```java
@Test
void signupUsesVietnameseMessageWhenRequested() throws Exception {
	mockMvc().perform(post("/api/v1/auth/signup")
					.header("Accept-Language", "vi")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"email":"signup-vi@example.com","password":"Password123!","displayName":"Signup Vi"}
							"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.message").value("Đăng ký thành công"))
			.andExpect(jsonPath("$.data.user.email").value("signup-vi@example.com"));
}
```

- [ ] **Step 2: Write failing Vietnamese duplicate email test**

Add this test method:

```java
@Test
void duplicateEmailUsesVietnameseMessageWhenRequested() throws Exception {
	String body = """
			{"email":"duplicate-vi@example.com","password":"Password123!","displayName":"Duplicate Vi"}
			""";

	mockMvc().perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk());

	mockMvc().perform(post("/api/v1/auth/signup")
					.header("Accept-Language", "vi")
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.message").value("Email đã được đăng ký"));
}
```

- [ ] **Step 3: Write failing Vietnamese cooldown interpolation test**

Add this test method:

```java
@Test
void resendVerificationOtpCooldownUsesVietnameseMessageWhenRequested() throws Exception {
	mockMvc().perform(post("/api/v1/auth/signup")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"email":"cooldown-vi@example.com","password":"Password123!"}
							"""))
			.andExpect(status().isOk());

	when(valueOperations.setIfAbsent(
			eq("email-verification-resend:cooldown-vi@example.com"),
			eq("1"),
			eq(Duration.ofSeconds(60)))).thenReturn(false);
	when(redisTemplate.getExpire("email-verification-resend:cooldown-vi@example.com", TimeUnit.SECONDS))
			.thenReturn(42L);

	mockMvc().perform(post("/api/v1/auth/resend-verification-otp")
					.header("Accept-Language", "vi")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"email":"cooldown-vi@example.com"}
							"""))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().string("Retry-After", "42"))
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.message")
					.value("Vui lòng chờ 42 giây trước khi yêu cầu mã OTP xác minh mới"));
}
```

- [ ] **Step 4: Run focused backend tests and verify RED**

Run from `CentralAuth-be`:

```powershell
.\mvnw.cmd -Dtest=AuthControllerIntegrationTests test
```

Expected: the new Vietnamese assertions fail because backend responses still use English messages.

- [ ] **Step 5: Commit the failing tests**

Run from the repo root:

```powershell
git add CentralAuth-be/src/test/java/com/centralauth/auth/AuthControllerIntegrationTests.java
git commit -m "test: add api i18n locale contract"
```

---

### Task 2: Backend Message Resolution

**Files:**
- Create: `CentralAuth-be/src/main/java/com/centralauth/common/LocalizedApiException.java`
- Create: `CentralAuth-be/src/main/java/com/centralauth/common/Messages.java`
- Create: `CentralAuth-be/src/main/resources/messages.properties`
- Create: `CentralAuth-be/src/main/resources/messages_vi.properties`
- Modify: `CentralAuth-be/src/main/resources/application.yml`
- Modify: `CentralAuth-be/src/main/java/com/centralauth/auth/*.java` exception files listed in the file map
- Modify: `CentralAuth-be/src/main/java/com/centralauth/auth/AuthController.java`
- Modify: `CentralAuth-be/src/main/java/com/centralauth/common/GlobalExceptionHandler.java`

- [ ] **Step 1: Add localized exception base**

Create `CentralAuth-be/src/main/java/com/centralauth/common/LocalizedApiException.java`:

```java
package com.centralauth.common;

public class LocalizedApiException extends RuntimeException {

	private final String messageCode;
	private final Object[] messageArgs;

	protected LocalizedApiException(String messageCode, Object... messageArgs) {
		super(messageCode);
		this.messageCode = messageCode;
		this.messageArgs = messageArgs.clone();
	}

	public String messageCode() {
		return messageCode;
	}

	public Object[] messageArgs() {
		return messageArgs.clone();
	}
}
```

- [ ] **Step 2: Add message resolver component**

Create `CentralAuth-be/src/main/java/com/centralauth/common/Messages.java`:

```java
package com.centralauth.common;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

@Component
public class Messages {

	private final MessageSource messageSource;

	public Messages(MessageSource messageSource) {
		this.messageSource = messageSource;
	}

	public String get(String code, Object... args) {
		return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
	}

	public String get(LocalizedApiException exception) {
		return get(exception.messageCode(), exception.messageArgs());
	}
}
```

- [ ] **Step 3: Add English message bundle**

Create `CentralAuth-be/src/main/resources/messages.properties`:

```properties
auth.signup.success=Signup successful
auth.signin.success=Signin successful
auth.email.verified=Email verified
auth.verificationOtp.resent=Verification OTP resent
auth.currentUser=Current user
auth.error.duplicateEmail=Email is already registered
auth.error.invalidCredentials=Invalid email or password
auth.error.invalidEmailVerificationOtp=Invalid or expired email verification OTP
auth.error.emailVerificationNotPending=Email verification is not pending for this email
auth.error.verificationOtpResendThrottled=Please wait {0} seconds before requesting another verification OTP
error.invalidRequest=Invalid request
```

- [ ] **Step 4: Add Vietnamese message bundle**

Create `CentralAuth-be/src/main/resources/messages_vi.properties`:

```properties
auth.signup.success=Đăng ký thành công
auth.signin.success=Đăng nhập thành công
auth.email.verified=Xác minh email thành công
auth.verificationOtp.resent=Đã gửi lại mã OTP xác minh
auth.currentUser=Người dùng hiện tại
auth.error.duplicateEmail=Email đã được đăng ký
auth.error.invalidCredentials=Email hoặc mật khẩu không đúng
auth.error.invalidEmailVerificationOtp=Mã OTP xác minh email không hợp lệ hoặc đã hết hạn
auth.error.emailVerificationNotPending=Email này không có yêu cầu xác minh đang chờ
auth.error.verificationOtpResendThrottled=Vui lòng chờ {0} giây trước khi yêu cầu mã OTP xác minh mới
error.invalidRequest=Yêu cầu không hợp lệ
```

- [ ] **Step 5: Configure message bundle fallback**

Add under the existing top-level `spring:` section in `CentralAuth-be/src/main/resources/application.yml`:

```yaml
  messages:
    basename: messages
    encoding: UTF-8
    fallback-to-system-locale: false
```

- [ ] **Step 6: Convert auth exceptions to message keys**

Update `DuplicateEmailException`:

```java
package com.centralauth.auth;

import com.centralauth.common.LocalizedApiException;

public class DuplicateEmailException extends LocalizedApiException {

	public DuplicateEmailException() {
		super("auth.error.duplicateEmail");
	}
}
```

Update `InvalidCredentialsException`:

```java
package com.centralauth.auth;

import com.centralauth.common.LocalizedApiException;

public class InvalidCredentialsException extends LocalizedApiException {

	public InvalidCredentialsException() {
		super("auth.error.invalidCredentials");
	}
}
```

Update `InvalidEmailVerificationOtpException`:

```java
package com.centralauth.auth;

import com.centralauth.common.LocalizedApiException;

public class InvalidEmailVerificationOtpException extends LocalizedApiException {

	public InvalidEmailVerificationOtpException() {
		super("auth.error.invalidEmailVerificationOtp");
	}
}
```

Update `EmailVerificationNotPendingException`:

```java
package com.centralauth.auth;

import com.centralauth.common.LocalizedApiException;

public class EmailVerificationNotPendingException extends LocalizedApiException {

	public EmailVerificationNotPendingException() {
		super("auth.error.emailVerificationNotPending");
	}
}
```

Update `EmailVerificationOtpResendThrottledException`:

```java
package com.centralauth.auth;

import com.centralauth.common.LocalizedApiException;

public class EmailVerificationOtpResendThrottledException extends LocalizedApiException {

	private final int retryAfterSeconds;

	public EmailVerificationOtpResendThrottledException(int retryAfterSeconds) {
		super("auth.error.verificationOtpResendThrottled", retryAfterSeconds);
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public int retryAfterSeconds() {
		return retryAfterSeconds;
	}
}
```

- [ ] **Step 7: Localize controller success messages**

Update `AuthController` constructor and methods so it has both dependencies:

```java
private final AuthService authService;
private final Messages messages;

public AuthController(AuthService authService, Messages messages) {
	this.authService = authService;
	this.messages = messages;
}
```

Add this import:

```java
import com.centralauth.common.Messages;
```

Replace success message literals with:

```java
return ApiResponse.success(messages.get("auth.signup.success"), authService.signup(request));
return ApiResponse.success(messages.get("auth.signin.success"), authService.signin(request));
return ApiResponse.success(messages.get("auth.email.verified"), null);
return ApiResponse.success(messages.get("auth.verificationOtp.resent"), authService.resendVerificationOtp(request));
return ApiResponse.success(messages.get("auth.currentUser"), authService.currentUser((String) authentication.getPrincipal()));
```

- [ ] **Step 8: Localize exception handler messages**

Update `GlobalExceptionHandler` with a `Messages` constructor dependency:

```java
private final Messages messages;

public GlobalExceptionHandler(Messages messages) {
	this.messages = messages;
}
```

Add imports:

```java
import com.centralauth.common.LocalizedApiException;
import com.centralauth.common.Messages;
```

Change validation handling:

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
	return error(messages.get("error.invalidRequest"), HttpStatus.BAD_REQUEST);
}
```

Change business exception handlers to call `messages.get(ex)`:

```java
return error(messages.get(ex), HttpStatus.CONFLICT);
return error(messages.get(ex), HttpStatus.UNAUTHORIZED);
return error(messages.get(ex), HttpStatus.BAD_REQUEST);
```

Change the throttled handler body to:

```java
.body(ApiResponse.error(messages.get(ex)));
```

- [ ] **Step 9: Run focused backend tests and verify GREEN**

Run from `CentralAuth-be`:

```powershell
.\mvnw.cmd -Dtest=AuthControllerIntegrationTests test
```

Expected: all auth controller integration tests pass, including English default and Vietnamese `Accept-Language` assertions.

- [ ] **Step 10: Commit backend implementation**

Run from the repo root:

```powershell
git add CentralAuth-be/src/main/java/com/centralauth/common CentralAuth-be/src/main/java/com/centralauth/auth CentralAuth-be/src/main/resources/application.yml CentralAuth-be/src/main/resources/messages.properties CentralAuth-be/src/main/resources/messages_vi.properties
git commit -m "feat: localize backend api messages"
```

---

### Task 3: Frontend I18n Core and HTTP Contract

**Files:**
- Create: `CentralAuth-fe/src/shared/i18n/messages.ts`
- Create: `CentralAuth-fe/src/shared/i18n/language.ts`
- Create: `CentralAuth-fe/src/shared/i18n/I18nContext.tsx`
- Create: `CentralAuth-fe/src/shared/i18n/useI18n.ts`
- Create: `CentralAuth-fe/src/shared/i18n/LanguageSwitcher.tsx`
- Modify: `CentralAuth-fe/src/app/providers/AppProviders.tsx`
- Modify: `CentralAuth-fe/src/shared/lib/http.ts`

- [ ] **Step 1: Add typed frontend dictionaries**

Create `CentralAuth-fe/src/shared/i18n/messages.ts`:

```typescript
export const messages = {
  en: {
    'common.requestFailed': 'Request failed',
    'common.yes': 'Yes',
    'common.no': 'No',
    'auth.emailAccess': 'Email access',
    'auth.signin': 'Sign in',
    'auth.signup': 'Sign up',
    'auth.displayName': 'Display name',
    'auth.displayName.placeholder': 'Your name',
    'auth.email': 'Email',
    'auth.password': 'Password',
    'auth.createAccount': 'Create account',
    'auth.emailVerifiedSignin': 'Email verified. Sign in to continue.',
    'auth.resendSent': 'A new OTP has been sent. Check your email to continue.',
    'auth.verifyEmail': 'Verify email',
    'auth.otp': 'OTP',
    'auth.resendOtp': 'Resend OTP',
    'auth.resendOtpIn': 'Resend OTP in {seconds}s',
    'auth.backToSignin': 'Back to sign in',
    'auth.validation.displayName.max': 'Display name must be 120 characters or fewer',
    'auth.validation.email.required': 'Please enter your email',
    'auth.validation.email.invalid': 'Please enter a valid email',
    'auth.validation.password.required': 'Please enter your password',
    'auth.validation.password.min': 'Password must be at least 8 characters',
    'auth.validation.password.max': 'Password must be 120 characters or fewer',
    'auth.validation.otp.required': 'Please enter the OTP',
    'auth.validation.otp.pattern': 'OTP must be 6 digits',
    'dashboard.title': 'CentralAuth Dashboard',
    'session.current': 'Current session',
    'session.signedIn': 'Signed in',
    'session.userId': 'User ID',
    'session.emailVerified': 'Email verified',
    'session.token': 'Token',
    'session.signOut': 'Sign out',
  },
  vi: {
    'common.requestFailed': 'Yêu cầu thất bại',
    'common.yes': 'Có',
    'common.no': 'Không',
    'auth.emailAccess': 'Truy cập bằng email',
    'auth.signin': 'Đăng nhập',
    'auth.signup': 'Đăng ký',
    'auth.displayName': 'Tên hiển thị',
    'auth.displayName.placeholder': 'Tên của bạn',
    'auth.email': 'Email',
    'auth.password': 'Mật khẩu',
    'auth.createAccount': 'Tạo tài khoản',
    'auth.emailVerifiedSignin': 'Email đã được xác minh. Đăng nhập để tiếp tục.',
    'auth.resendSent': 'Mã OTP mới đã được gửi. Kiểm tra email để tiếp tục.',
    'auth.verifyEmail': 'Xác minh email',
    'auth.otp': 'OTP',
    'auth.resendOtp': 'Gửi lại OTP',
    'auth.resendOtpIn': 'Gửi lại OTP sau {seconds}s',
    'auth.backToSignin': 'Quay lại đăng nhập',
    'auth.validation.displayName.max': 'Tên hiển thị không được vượt quá 120 ký tự',
    'auth.validation.email.required': 'Vui lòng nhập email',
    'auth.validation.email.invalid': 'Vui lòng nhập email hợp lệ',
    'auth.validation.password.required': 'Vui lòng nhập mật khẩu',
    'auth.validation.password.min': 'Mật khẩu phải có ít nhất 8 ký tự',
    'auth.validation.password.max': 'Mật khẩu không được vượt quá 120 ký tự',
    'auth.validation.otp.required': 'Vui lòng nhập OTP',
    'auth.validation.otp.pattern': 'OTP phải gồm 6 chữ số',
    'dashboard.title': 'Bảng điều khiển CentralAuth',
    'session.current': 'Phiên hiện tại',
    'session.signedIn': 'Đã đăng nhập',
    'session.userId': 'ID người dùng',
    'session.emailVerified': 'Email đã xác minh',
    'session.token': 'Token',
    'session.signOut': 'Đăng xuất',
  },
} as const

export type Language = keyof typeof messages
export type MessageKey = keyof typeof messages.en
export type MessageParams = Record<string, string | number>

export function formatMessage(template: string, params: MessageParams = {}) {
  return Object.entries(params).reduce(
    (message, [key, value]) => message.replaceAll(`{${key}}`, String(value)),
    template,
  )
}

export function translate(language: Language, key: MessageKey, params?: MessageParams) {
  return formatMessage(messages[language][key], params)
}
```

- [ ] **Step 2: Add language persistence helpers**

Create `CentralAuth-fe/src/shared/i18n/language.ts`:

```typescript
import type { Language } from './messages'

export const languageStorageKey = 'centralauth.language'
export const supportedLanguages = ['en', 'vi'] as const satisfies readonly Language[]

export function normalizeLanguage(value: string | null | undefined): Language | null {
  if (!value) return null
  const normalized = value.toLowerCase()
  if (normalized.startsWith('vi')) return 'vi'
  if (normalized.startsWith('en')) return 'en'
  return null
}

export function getStoredLanguage() {
  return normalizeLanguage(localStorage.getItem(languageStorageKey))
}

export function getBrowserLanguage() {
  return normalizeLanguage(navigator.language)
}

export function getCurrentLanguage(): Language {
  return getStoredLanguage() ?? getBrowserLanguage() ?? 'en'
}

export function storeLanguage(language: Language) {
  localStorage.setItem(languageStorageKey, language)
}
```

- [ ] **Step 3: Add i18n context**

Create `CentralAuth-fe/src/shared/i18n/I18nContext.tsx`:

```tsx
import { createContext, useCallback, useMemo, useState } from 'react'
import type { PropsWithChildren } from 'react'
import { getCurrentLanguage, storeLanguage } from './language'
import type { Language, MessageKey, MessageParams } from './messages'
import { translate } from './messages'

type I18nContextValue = {
  language: Language
  setLanguage: (language: Language) => void
  t: (key: MessageKey, params?: MessageParams) => string
}

export const I18nContext = createContext<I18nContextValue | null>(null)

export function I18nProvider({ children }: PropsWithChildren) {
  const [language, setLanguageState] = useState<Language>(() => getCurrentLanguage())

  const setLanguage = useCallback((nextLanguage: Language) => {
    storeLanguage(nextLanguage)
    setLanguageState(nextLanguage)
  }, [])

  const t = useCallback(
    (key: MessageKey, params?: MessageParams) => translate(language, key, params),
    [language],
  )

  const value = useMemo(() => ({ language, setLanguage, t }), [language, setLanguage, t])

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>
}
```

- [ ] **Step 4: Add i18n hook**

Create `CentralAuth-fe/src/shared/i18n/useI18n.ts`:

```typescript
import { useContext } from 'react'
import { I18nContext } from './I18nContext'

export function useI18n() {
  const context = useContext(I18nContext)
  if (!context) {
    throw new Error('useI18n must be used within I18nProvider')
  }
  return context
}
```

- [ ] **Step 5: Add language switcher**

Create `CentralAuth-fe/src/shared/i18n/LanguageSwitcher.tsx`:

```tsx
import { Segmented } from 'antd'
import type { Language } from './messages'
import { useI18n } from './useI18n'

export function LanguageSwitcher() {
  const { language, setLanguage } = useI18n()

  return (
    <Segmented<Language>
      size="small"
      value={language}
      options={[
        { label: 'EN', value: 'en' },
        { label: 'VI', value: 'vi' },
      ]}
      onChange={setLanguage}
    />
  )
}
```

- [ ] **Step 6: Wire provider and Ant Design locale**

Modify `CentralAuth-fe/src/app/providers/AppProviders.tsx` to wrap providers like this:

```tsx
import type { PropsWithChildren } from 'react'
import { ConfigProvider } from 'antd'
import enUS from 'antd/locale/en_US'
import viVN from 'antd/locale/vi_VN'
import { BrowserRouter } from 'react-router-dom'
import { AuthSessionProvider } from '../../features/auth/context/AuthSessionContext'
import { I18nProvider } from '../../shared/i18n/I18nContext'
import { useI18n } from '../../shared/i18n/useI18n'

function LocalizedConfigProvider({ children }: PropsWithChildren) {
  const { language } = useI18n()

  return (
    <ConfigProvider
      locale={language === 'vi' ? viVN : enUS}
      theme={{
        token: {
          colorPrimary: '#246bfe',
          borderRadius: 10,
        },
      }}
    >
      {children}
    </ConfigProvider>
  )
}

export function AppProviders({ children }: PropsWithChildren) {
  return (
    <I18nProvider>
      <LocalizedConfigProvider>
        <BrowserRouter>
          <AuthSessionProvider>{children}</AuthSessionProvider>
        </BrowserRouter>
      </LocalizedConfigProvider>
    </I18nProvider>
  )
}
```

- [ ] **Step 7: Send Accept-Language and localize HTTP fallbacks**

Modify `CentralAuth-fe/src/shared/lib/http.ts` so `apiRequest` uses these helpers:

```typescript
import { getCurrentLanguage } from '../i18n/language'
import { translate } from '../i18n/messages'
```

```typescript
export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, withLanguageHeader(init))
  const payload = (await response.json().catch(() => null)) as ApiResponse<T> | null
  const language = getCurrentLanguage()
  const fallbackMessage = translate(language, 'common.requestFailed')

  if (!response.ok) {
    throw new ApiRequestError(
      payload?.message ?? fallbackMessage,
      response.status,
      retryAfterSeconds(response.headers),
    )
  }

  if (!payload?.success) {
    throw new ApiRequestError(payload?.message ?? fallbackMessage, response.status)
  }

  return payload.data
}

function withLanguageHeader(init?: RequestInit): RequestInit {
  const headers = new Headers(init?.headers)
  if (!headers.has('Accept-Language')) {
    headers.set('Accept-Language', getCurrentLanguage())
  }
  return { ...init, headers }
}
```

- [ ] **Step 8: Verify frontend core compiles**

Run from `CentralAuth-fe`:

```powershell
npm run build
```

Expected: TypeScript fails because UI components still contain untranslated strings or imports are not yet used, or passes if TypeScript accepts unused translation infrastructure. Continue to Task 4 either way.

- [ ] **Step 9: Commit frontend i18n core**

Run from the repo root:

```powershell
git add CentralAuth-fe/src/shared/i18n CentralAuth-fe/src/app/providers/AppProviders.tsx CentralAuth-fe/src/shared/lib/http.ts
git commit -m "feat: add frontend i18n core"
```

---

### Task 4: Frontend UI Translation

**Files:**
- Modify: `CentralAuth-fe/src/features/auth/pages/AuthPage.tsx`
- Modify: `CentralAuth-fe/src/features/auth/components/AuthFormCard.tsx`
- Modify: `CentralAuth-fe/src/features/auth/components/VerifyEmailCard.tsx`
- Modify: `CentralAuth-fe/src/features/dashboard/pages/DashboardPage.tsx`
- Modify: `CentralAuth-fe/src/features/auth/components/SessionCard.tsx`

- [ ] **Step 1: Translate auth page messages and header**

In `AuthPage.tsx`, import:

```typescript
import { LanguageSwitcher } from '../../../shared/i18n/LanguageSwitcher'
import { useI18n } from '../../../shared/i18n/useI18n'
```

Inside `AuthPage`, add:

```typescript
const { t } = useI18n()
```

Replace request fallback strings with:

```typescript
setError(requestError instanceof Error ? requestError.message : t('common.requestFailed'))
```

Replace resend success text with:

```typescript
setResendMessage(t('auth.resendSent'))
```

Replace verified alert message with:

```tsx
message={t('auth.emailVerifiedSignin')}
```

Replace the brand-only header with:

```tsx
<Space align="center" style={{ width: '100%', justifyContent: 'space-between' }}>
  <Typography.Text className="app-brand">CentralAuth</Typography.Text>
  <LanguageSwitcher />
</Space>
```

- [ ] **Step 2: Translate auth form card**

In `AuthFormCard.tsx`, import and use the hook:

```typescript
import { useI18n } from '../../../shared/i18n/useI18n'
```

```typescript
const { t } = useI18n()
```

Replace the card title:

```tsx
<Card title={t('auth.emailAccess')}>
```

Replace segmented options:

```tsx
options={[
  { label: t('auth.signin'), value: 'signin' },
  { label: t('auth.signup'), value: 'signup' },
]}
```

Replace labels, placeholder, rules, and submit button:

```tsx
label={t('auth.displayName')}
placeholder={t('auth.displayName.placeholder')}
rules={[{ max: 120, message: t('auth.validation.displayName.max') }]}
label={t('auth.email')}
rules={[
  { required: true, message: t('auth.validation.email.required') },
  { type: 'email', message: t('auth.validation.email.invalid') },
]}
label={t('auth.password')}
rules={[
  { required: true, message: t('auth.validation.password.required') },
  {
    min: mode === 'signup' ? 8 : 1,
    message: mode === 'signup'
      ? t('auth.validation.password.min')
      : t('auth.validation.password.required'),
  },
  { max: 120, message: t('auth.validation.password.max') },
]}
{mode === 'signup' ? t('auth.createAccount') : t('auth.signin')}
```

- [ ] **Step 3: Translate verify email card**

In `VerifyEmailCard.tsx`, import and use:

```typescript
import { useI18n } from '../../../shared/i18n/useI18n'
```

```typescript
const { t } = useI18n()
```

Replace title, labels, rules, and buttons:

```tsx
<Card title={t('auth.verifyEmail')}>
<Form.Item label={t('auth.email')}>
<Form.Item
  label={t('auth.otp')}
  name="otp"
  rules={[
    { required: true, message: t('auth.validation.otp.required') },
    { pattern: /^\d{6}$/, message: t('auth.validation.otp.pattern') },
  ]}
>
```

Replace `resendLabel`:

```typescript
const resendLabel =
  resendCooldownSeconds > 0
    ? t('auth.resendOtpIn', { seconds: resendCooldownSeconds })
    : t('auth.resendOtp')
```

Replace button text:

```tsx
{t('auth.verifyEmail')}
{resendLabel}
{t('auth.backToSignin')}
```

- [ ] **Step 4: Translate dashboard page header**

In `DashboardPage.tsx`, import:

```typescript
import { LanguageSwitcher } from '../../../shared/i18n/LanguageSwitcher'
import { useI18n } from '../../../shared/i18n/useI18n'
```

Inside the component:

```typescript
const { t } = useI18n()
```

Replace the title header with:

```tsx
<Space align="center" style={{ width: '100%', justifyContent: 'space-between' }}>
  <Typography.Text className="app-brand">{t('dashboard.title')}</Typography.Text>
  <LanguageSwitcher />
</Space>
```

- [ ] **Step 5: Translate session card**

In `SessionCard.tsx`, import and use:

```typescript
import { useI18n } from '../../../shared/i18n/useI18n'
```

```typescript
const { t } = useI18n()
```

Replace labels and button text:

```tsx
<Card title={t('session.current')}>
{t('session.signedIn')}
<Descriptions.Item label={t('session.userId')}>{user.id}</Descriptions.Item>
<Descriptions.Item label={t('session.emailVerified')}>
  {user.emailVerified ? t('common.yes') : t('common.no')}
</Descriptions.Item>
<Descriptions.Item label={t('session.token')}>{tokenPreview}</Descriptions.Item>
<Button onClick={onSignOut}>{t('session.signOut')}</Button>
```

- [ ] **Step 6: Run frontend build and lint**

Run from `CentralAuth-fe`:

```powershell
npm run build
npm run lint
```

Expected: both commands pass with no TypeScript or ESLint errors.

- [ ] **Step 7: Commit frontend UI translation**

Run from the repo root:

```powershell
git add CentralAuth-fe/src/features CentralAuth-fe/src/shared/i18n CentralAuth-fe/src/shared/lib/http.ts CentralAuth-fe/src/app/providers/AppProviders.tsx
git commit -m "feat: localize frontend auth UI"
```

---

### Task 5: Full Verification

**Files:**
- No source changes expected.

- [ ] **Step 1: Run backend full test suite**

Run from `CentralAuth-be`:

```powershell
.\mvnw.cmd test
```

Expected: all backend tests pass.

- [ ] **Step 2: Run frontend production build**

Run from `CentralAuth-fe`:

```powershell
npm run build
```

Expected: TypeScript build and Vite production build pass.

- [ ] **Step 3: Run frontend lint**

Run from `CentralAuth-fe`:

```powershell
npm run lint
```

Expected: ESLint exits successfully.

- [ ] **Step 4: Inspect final diff**

Run from the repo root:

```powershell
git status --short
git diff --stat HEAD
```

Expected: worktree only contains intended i18n changes from the final uncommitted task, or is clean if every task was committed.

---

## Self-Review

- Spec coverage: frontend dictionaries, persisted language, browser-language default, Ant Design locale, language switcher, `Accept-Language`, backend message bundles, localized success/error responses, fallback English, validation fallback, cooldown interpolation, and verification commands are covered.
- Placeholder scan: the plan contains no TBD markers, no deferred implementation notes, and no unspecific "handle later" steps.
- Type consistency: `Language`, `MessageKey`, `MessageParams`, `I18nProvider`, `useI18n`, `LocalizedApiException`, and `Messages` are named consistently across tasks.
