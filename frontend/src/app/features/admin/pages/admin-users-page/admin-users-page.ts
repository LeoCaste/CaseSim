import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

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
type UserFilter = 'active' | 'inactive' | 'all';

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
  private readonly destroyRef = inject(DestroyRef);
  private readonly cdr = inject(ChangeDetectorRef);

  roleOptions: AdminUserRoleOption[] = [];

  users: AdminUser[] = [];
  loading = false;
  isSubmitting = false;
  loadError = '';
  formError = '';
  formSuccess = '';

  currentFilter: UserFilter = 'active';
  togglingUserId: string | null = null;
  deletingUserId: string | null = null;

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
    this.fetchUsers('active');
  }

  fetchRoles(): void {
    this.adminUserService
      .getRoles()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (roles) => {
          this.roleOptions = roles;
          if (roles.length > 0 && !roles.some((role) => role.code === this.form.role)) {
            this.form.role = roles[0].code;
          }
          this.triggerViewUpdate();
        },
        error: () => {
          this.formError = 'No fue posible cargar roles de usuario.';
          this.triggerViewUpdate();
        }
      });
  }

  onFilterChange(filter: UserFilter): void {
    this.currentFilter = filter;
    this.fetchUsers(filter);
  }

  fetchUsers(filter: UserFilter = this.currentFilter): void {
    this.currentFilter = filter;
    this.loading = true;
    this.loadError = '';
    this.triggerViewUpdate();

    const activeFilter = this.toBackendFilter(filter);

    this.adminUserService
      .getUsers(activeFilter)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.loading = false;
          this.triggerViewUpdate();
        })
      )
      .subscribe({
        next: (users) => {
          this.users = users;
          this.loadError = '';
          this.triggerViewUpdate();
        },
        error: (error: AdminUserApiError) => {
          this.users = [];
          this.loadError = this.resolveErrorMessage(error, 'No fue posible cargar usuarios.');
          this.triggerViewUpdate();
        }
      });
  }

  startCreate(): void {
    this.formMode = 'create';
    this.editingUserId = null;
    this.formError = '';
    this.formSuccess = '';
    this.form = this.buildInitialForm();
    this.triggerViewUpdate();
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
    this.triggerViewUpdate();
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
    this.triggerViewUpdate();

    const payload = this.buildPayload();
    const request$ = this.formMode === 'create' || !this.editingUserId
      ? this.adminUserService.createUser(payload)
      : this.adminUserService.updateUser(this.editingUserId, payload);

    request$
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.isSubmitting = false;
          this.triggerViewUpdate();
        })
      )
      .subscribe({
        next: (savedUser) => {
          if (this.formMode === 'create') {
            if (this.shouldDisplayUser(savedUser, this.currentFilter)) {
              this.users = [...this.users, savedUser].sort((a, b) => a.name.localeCompare(b.name, 'es'));
            }
            this.formSuccess = 'Usuario creado correctamente.';
            this.form = this.buildInitialForm();
          } else {
            if (this.shouldDisplayUser(savedUser, this.currentFilter)) {
              this.users = this.users
                .map((user) => (user.id === savedUser.id ? savedUser : user))
                .sort((a, b) => a.name.localeCompare(b.name, 'es'));
            } else {
              this.users = this.users.filter((user) => user.id !== savedUser.id);
            }
            this.formSuccess = 'Usuario actualizado correctamente.';
            this.formMode = 'create';
            this.editingUserId = null;
            this.form = this.buildInitialForm();
          }
          this.triggerViewUpdate();
        },
        error: (error: AdminUserApiError) => {
          this.formError = this.resolveErrorMessage(error, 'No fue posible guardar el usuario.');
          this.triggerViewUpdate();
        }
      });
  }

  toggleStatus(user: AdminUser): void {
    this.formError = '';
    this.formSuccess = '';
    this.togglingUserId = user.id;
    this.triggerViewUpdate();

    this.adminUserService
      .toggleStatus(user.id, !user.active)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.togglingUserId = null;
          this.triggerViewUpdate();
        })
      )
      .subscribe({
        next: (updatedUser) => {
          if (this.currentFilter === 'all') {
            this.users = this.users.map((item) => (item.id === updatedUser.id ? updatedUser : item));
          } else if (this.shouldDisplayUser(updatedUser, this.currentFilter)) {
            this.users = this.users.map((item) => (item.id === updatedUser.id ? updatedUser : item));
          } else {
            this.users = this.users.filter((item) => item.id !== updatedUser.id);
          }
          this.formSuccess = `Usuario ${updatedUser.active ? 'activado' : 'desactivado'} correctamente.`;
          this.triggerViewUpdate();
        },
        error: (error: AdminUserApiError) => {
          this.formError = this.resolveErrorMessage(error, 'No fue posible actualizar el estado del usuario.');
          this.triggerViewUpdate();
        }
      });
  }

  deleteUser(user: AdminUser): void {
    this.formError = '';
    this.formSuccess = '';

    const confirmed = window.confirm(`¿Confirmas la eliminación permanente del usuario "${user.name}"?`);
    if (!confirmed) {
      return;
    }

    this.deletingUserId = user.id;
    this.triggerViewUpdate();

    this.adminUserService
      .deleteUser(user.id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.deletingUserId = null;
          this.triggerViewUpdate();
        })
      )
      .subscribe({
        next: () => {
          this.users = this.users.filter((item) => item.id !== user.id);

          if (this.editingUserId === user.id) {
            this.startCreate();
          }

          this.formSuccess = 'Usuario eliminado correctamente.';
          this.triggerViewUpdate();
        },
        error: (error: AdminUserApiError) => {
          this.formError = this.resolveErrorMessage(error, 'No fue posible eliminar el usuario.');
          this.triggerViewUpdate();
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

  isDeletingUser(userId: string): boolean {
    return this.deletingUserId === userId;
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

    if (error.message?.trim()) {
      return error.message;
    }

    return fallback;
  }

  private triggerViewUpdate(): void {
    if (this.destroyRef.destroyed) {
      return;
    }

    this.cdr.detectChanges();
  }

  private toBackendFilter(filter: UserFilter): 'true' | 'false' | 'all' {
    if (filter === 'active') {
      return 'true';
    }

    if (filter === 'inactive') {
      return 'false';
    }

    return 'all';
  }

  private shouldDisplayUser(user: AdminUser, filter: UserFilter): boolean {
    if (filter === 'all') {
      return true;
    }

    return filter === 'active' ? user.active : !user.active;
  }
}
