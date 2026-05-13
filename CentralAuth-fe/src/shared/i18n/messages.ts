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
    'session.signOutAllDevices': 'Sign out all devices',
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
    'session.signOutAllDevices': 'Đăng xuất tất cả thiết bị',
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
