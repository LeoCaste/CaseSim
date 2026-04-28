import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';

import {
  AdminUser,
  AdminUserApiError,
  AdminUserCreatePayload,
  AdminUserRole,
  AdminUserRoleOption,
  getAdminUserRoleLabel
} from '../../../../core/models/admin-user.model';
import { AdminUserService } from '../../../../core/services/admin-user.service';
import { UserContext } from '../../../../core/services/user-context';

type UserFormMode = 'create' | 'edit';

interface AdminUserFormModel {
  name: string;
  email: string;
  role: AdminUserRole;
  password: string;
}

@Component({
  selector: 'app-admin-users-page',
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-users-page.html',
  styleUrl: './admin-users-page.css'
})
export class AdminUsersPage implements OnInit {
  roleOptions: AdminUserRoleOption[] = [];

  users: AdminUser[] = [];
  loading = false;
  isSubmitting = false;
  loadError = '';
  formError = '';
  formSuccess = '';

  togglingUserId: string | null = null;

  formMode: UserFormMode = 'create';
  editingUserId: string | null = null;
  form: AdminUserFormModel = this.buildInitialForm();

  constructor(
    private adminUserService: AdminUserService,
    private userContext: UserContext
  ) {
    this.userContext.setRole('admin');
  }

  ngOnInit(): void {
    this.fetchRoles();
    this.fetchUsers();
  }

  fetchRoles(): void {
    this.adminUserService.getRoles().subscribe({
      next: (roles) => {
        this.roleOptions = roles;
        if (roles.length > 0 && !roles.some((role) => role.code === this.form.role)) {
          this.form.role = roles[0].code;
        }
      },
      error: () => {
        this.formError = 'No fue posible cargar roles de usuario.';
      }
    });
  }

  fetchUsers(): void {
    this.loading = true;
    this.loadError = '';

    this.adminUserService.getUsers().subscribe({
      next: (users) => {
        this.users = users;
        this.loading = false;
      },
      error: (error: AdminUserApiError) => {
        this.loadError = this.resolveErrorMessage(error, 'No fue posible cargar usuarios.');
        this.loading = false;
      }
    });
  }

  startCreate(): void {
    this.formMode = 'create';
    this.editingUserId = null;
    this.formError = '';
    this.formSuccess = '';
    this.form = this.buildInitialForm();
  }

  startEdit(user: AdminUser): void {
    this.formMode = 'edit';
    this.editingUserId = user.id;
    this.formError = '';
    this.formSuccess = '';
    this.form = {
      name: user.name,
      email: user.email,
      role: user.role,
      password: ''
    };
  }

  onRoleChange(role: string): void {
    this.form.role = role;
    if (!this.requiresPassword()) {
      this.form.password = '';
    }
  }

  submitForm(): void {
    this.formError = '';
    this.formSuccess = '';

    const validationError = this.validateForm();
    if (validationError) {
      this.formError = validationError;
      return;
    }

    this.isSubmitting = true;

    const payload = this.buildPayload();
    const request$ = this.formMode === 'create' || !this.editingUserId
      ? this.adminUserService.createUser(payload)
      : this.adminUserService.updateUser(this.editingUserId, payload);

    request$.subscribe({
      next: (savedUser) => {
        if (this.formMode === 'create') {
          this.users = [...this.users, savedUser].sort((a, b) => a.name.localeCompare(b.name, 'es'));
          this.formSuccess = 'Usuario creado correctamente.';
          this.form = this.buildInitialForm();
        } else {
          this.users = this.users
            .map((user) => (user.id === savedUser.id ? savedUser : user))
            .sort((a, b) => a.name.localeCompare(b.name, 'es'));
          this.formSuccess = 'Usuario actualizado correctamente.';
        }

        this.isSubmitting = false;
      },
      error: (error: AdminUserApiError) => {
        this.formError = this.resolveErrorMessage(error, 'No fue posible guardar el usuario.');
        this.isSubmitting = false;
      }
    });
  }

  toggleStatus(user: AdminUser): void {
    this.formError = '';
    this.formSuccess = '';
    this.togglingUserId = user.id;

    this.adminUserService.toggleStatus(user.id, !user.active).subscribe({
      next: (updatedUser) => {
        this.users = this.users.map((item) => (item.id === updatedUser.id ? updatedUser : item));
        this.togglingUserId = null;
      },
      error: (error: AdminUserApiError) => {
        this.formError = this.resolveErrorMessage(error, 'No fue posible actualizar el estado del usuario.');
        this.togglingUserId = null;
      }
    });
  }

  getRoleLabel(role: AdminUserRole): string {
    return getAdminUserRoleLabel(role);
  }

  requiresPassword(): boolean {
    return this.form.role === 'ADMIN';
  }

  isTogglingUser(userId: string): boolean {
    return this.togglingUserId === userId;
  }

  private validateForm(): string | null {
    if (!this.form.name.trim() || !this.form.email.trim()) {
      return 'Completa nombre y correo para continuar.';
    }

    if (this.requiresPassword() && !this.form.password.trim()) {
      return 'La contraseña es obligatoria para usuarios con rol Administrador.';
    }

    return null;
  }

  private buildPayload(): AdminUserCreatePayload {
    const payload: AdminUserCreatePayload = {
      name: this.form.name.trim(),
      email: this.form.email.trim().toLowerCase(),
      role: this.form.role
    };

    if (this.requiresPassword()) {
      payload.password = this.form.password.trim();
    }

    return payload;
  }

  private buildInitialForm(): AdminUserFormModel {
    return {
      name: '',
      email: '',
      role: 'ADMIN',
      password: ''
    };
  }

  private resolveErrorMessage(error: AdminUserApiError | null | undefined, fallback: string): string {
    if (!error) {
      return fallback;
    }

    if (error.code === 'UNAUTHORIZED' || error.code === 'FORBIDDEN' || error.code === 'CONFLICT' || error.code === 'VALIDATION') {
      return error.message;
    }

    return fallback;
  }
}
