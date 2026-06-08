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

export interface BootstrapAdminStatusResponse {
  adminExists: boolean;
}

export interface BootstrapAdminRequest {
  email: string;
  password: string;
  confirmPassword: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ForgotPasswordResponse {
  message?: string;
}

export interface ResetPasswordRequest {
  token: string;
  password: string;
  confirmPassword: string;
}
