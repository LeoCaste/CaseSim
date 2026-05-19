export interface AuthPreCheckRequest {
  email: string;
}

export interface AuthPreCheckResponse {
  requiresPassword: boolean;
}

export interface AuthLoginRequest {
  email: string;
  password?: string;
}

export interface BootstrapStatusResponse {
  needsInitialSetup: boolean;
}

export interface BootstrapAdminRequest {
  institutionName: string;
  adminName: string;
  adminEmail: string;
  password: string;
  confirmPassword: string;
  bootstrapToken: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  password: string;
  confirmPassword: string;
}
