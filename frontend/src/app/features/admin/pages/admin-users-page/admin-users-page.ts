import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, DestroyRef, HostListener, OnInit, inject } from '@angular/core';
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
type UserStatusFilter = 'active' | 'inactive' | 'all';

interface ConfirmDialogState {
  type: 'delete' | 'toggle';
  user: AdminUser;
}

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
  isProcessingConfirmation = false;
  loadError = '';
  pageError = '';
  pageSuccess = '';

  hasSubmitted = false;
  roleFilter: AdminUserRole | 'all' = 'all';
  statusFilter: UserStatusFilter = 'active';
  searchTerm = '';

  openMenuUserId: string | null = null;
  confirmDialog: ConfirmDialogState | null = null;

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
    this.fetchUsers();
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
          this.pageError = 'No fue posible cargar roles de usuario.';
          this.triggerViewUpdate();
        }
      });
  }

  fetchUsers(): void {
    this.loading = true;
    this.loadError = '';
    this.pageError = '';
    this.triggerViewUpdate();

    this.adminUserService
      .getUsers('all')
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
          this.loadError = this.resolveErrorMessage(error, 'No se pudieron cargar los usuarios. Intenta nuevamente.');
          this.triggerViewUpdate();
        }
      });
  }

  startCreate(): void {
    this.formMode = 'create';
    this.editingUserId = null;
    this.hasSubmitted = false;
    this.form = this.buildInitialForm();
    this.triggerViewUpdate();
  }

  startEdit(user: AdminUser): void {
    this.formMode = 'edit';
    this.editingUserId = user.id;
    this.hasSubmitted = false;
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
    this.triggerViewUpdate();
  }

  submitForm(): void {
    if (this.isSubmitting) {
      return;
    }

    this.pageError = '';
    this.pageSuccess = '';
    this.hasSubmitted = true;

    if (!this.isFormValid()) {
      this.triggerViewUpdate();
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
            this.users = [...this.users, savedUser].sort((a, b) => a.name.localeCompare(b.name, 'es'));
            this.pageSuccess = 'Usuario creado correctamente.';
            this.form = this.buildInitialForm();
            this.hasSubmitted = false;
          } else {
            this.users = this.users
              .map((user) => (user.id === savedUser.id ? savedUser : user))
              .sort((a, b) => a.name.localeCompare(b.name, 'es'));
            this.pageSuccess = 'Usuario actualizado correctamente.';
            this.formMode = 'create';
            this.editingUserId = null;
            this.form = this.buildInitialForm();
            this.hasSubmitted = false;
          }
          this.triggerViewUpdate();
        },
        error: () => {
          this.pageError = this.formMode === 'create'
            ? 'No se pudo crear el usuario. Intenta nuevamente.'
            : 'No se pudo actualizar el usuario. Intenta nuevamente.';
          this.triggerViewUpdate();
        }
      });
  }

  openToggleConfirmation(user: AdminUser): void {
    this.closeMenu();
    this.confirmDialog = { type: 'toggle', user };
    this.triggerViewUpdate();
  }

  confirmToggleStatus(user: AdminUser): void {
    this.pageError = '';
    this.pageSuccess = '';
    this.isProcessingConfirmation = true;
    this.togglingUserId = user.id;
    this.triggerViewUpdate();

    this.adminUserService
      .toggleStatus(user.id, !user.active)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.togglingUserId = null;
          this.isProcessingConfirmation = false;
          this.triggerViewUpdate();
        })
      )
      .subscribe({
        next: (updatedUser) => {
          this.users = this.users.map((item) => (item.id === updatedUser.id ? updatedUser : item));
          this.pageSuccess = `Usuario ${updatedUser.active ? 'activado' : 'desactivado'} correctamente.`;
          this.closeConfirmDialog();
          this.triggerViewUpdate();
        },
        error: () => {
          this.pageError = 'No se pudo actualizar el estado del usuario.';
          this.triggerViewUpdate();
        }
      });
  }

  openDeleteConfirmation(user: AdminUser): void {
    this.closeMenu();
    this.confirmDialog = { type: 'delete', user };
    this.triggerViewUpdate();
  }

  confirmDeleteUser(user: AdminUser): void {
    this.pageError = '';
    this.pageSuccess = '';
    this.isProcessingConfirmation = true;

    this.deletingUserId = user.id;
    this.triggerViewUpdate();

    this.adminUserService
      .deleteUser(user.id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.deletingUserId = null;
          this.isProcessingConfirmation = false;
          this.triggerViewUpdate();
        })
      )
      .subscribe({
        next: () => {
          this.users = this.users.filter((item) => item.id !== user.id);

          if (this.editingUserId === user.id) {
            this.startCreate();
          }

          this.pageSuccess = 'Usuario eliminado correctamente.';
          this.closeConfirmDialog();
          this.triggerViewUpdate();
        },
        error: () => {
          this.pageError = 'No se pudo eliminar el usuario.';
          this.triggerViewUpdate();
        }
      });
  }

  closeConfirmDialog(): void {
    if (this.isProcessingConfirmation) {
      return;
    }

    this.confirmDialog = null;
    this.triggerViewUpdate();
  }

  getRoleLabel(role: AdminUserRole): string {
    return getAdminUserRoleLabel(role);
  }

  requiresPassword(): boolean {
    return this.form.role === 'ADMIN';
  }

  get emailPlaceholder(): string {
    return this.form.role === 'ESTUDIANTE' ? 'usuario@ufromail.cl' : 'usuario@ufrontera.cl';
  }

  get filteredUsers(): AdminUser[] {
    const normalizedSearch = this.searchTerm.trim().toLowerCase();
    return this.users.filter((user) => {
      const matchesRole = this.roleFilter === 'all' || user.role === this.roleFilter;
      const matchesStatus = this.statusFilter === 'all' || (this.statusFilter === 'active' ? user.active : !user.active);
      const matchesSearch =
        normalizedSearch.length === 0 ||
        user.name.toLowerCase().includes(normalizedSearch) ||
        user.email.toLowerCase().includes(normalizedSearch);

      return matchesRole && matchesStatus && matchesSearch;
    });
  }

  get hasAnyUsers(): boolean {
    return this.users.length > 0;
  }

  get hasActiveFilters(): boolean {
    return this.searchTerm.trim().length > 0 || this.roleFilter !== 'all' || this.statusFilter !== 'all';
  }

  clearFilters(): void {
    this.searchTerm = '';
    this.roleFilter = 'all';
    this.statusFilter = 'all';
    this.triggerViewUpdate();
  }

  toggleMenu(userId: string): void {
    this.openMenuUserId = this.openMenuUserId === userId ? null : userId;
    this.triggerViewUpdate();
  }

  closeMenu(): void {
    this.openMenuUserId = null;
    this.triggerViewUpdate();
  }

  isMenuOpen(userId: string): boolean {
    return this.openMenuUserId === userId;
  }

  onRowActionEdit(user: AdminUser): void {
    this.closeMenu();
    this.startEdit(user);
  }

  onRowActionToggle(user: AdminUser): void {
    this.openToggleConfirmation(user);
  }

  onRowActionDelete(user: AdminUser): void {
    this.openDeleteConfirmation(user);
  }

  isTogglingUser(userId: string): boolean {
    return this.togglingUserId === userId;
  }

  isDeletingUser(userId: string): boolean {
    return this.deletingUserId === userId;
  }

  isStatusActionLoading(userId: string): boolean {
    return this.togglingUserId === userId && this.isProcessingConfirmation;
  }

  isDeleteActionLoading(userId: string): boolean {
    return this.deletingUserId === userId && this.isProcessingConfirmation;
  }

  getNameError(): string {
    if (!this.hasSubmitted) {
      return '';
    }

    return this.form.name.trim() ? '' : 'El nombre es obligatorio.';
  }

  getEmailError(): string {
    if (!this.hasSubmitted) {
      return '';
    }

    if (!this.form.email.trim()) {
      return 'El correo electrónico es obligatorio.';
    }

    if (!this.isValidEmail(this.form.email)) {
      return 'Ingresa un correo electrónico válido.';
    }

    return '';
  }

  getPasswordError(): string {
    if (!this.hasSubmitted || !this.requiresPassword()) {
      return '';
    }

    return this.form.password.trim() ? '' : 'La contraseña inicial es obligatoria para administradores.';
  }

  getConfirmTitle(): string {
    if (!this.confirmDialog) {
      return '';
    }

    if (this.confirmDialog.type === 'delete') {
      return 'Eliminar usuario';
    }

    return this.confirmDialog.user.active ? 'Desactivar usuario' : 'Activar usuario';
  }

  getConfirmMessage(): string {
    if (!this.confirmDialog) {
      return '';
    }

    if (this.confirmDialog.type === 'delete') {
      return `¿Seguro que deseas eliminar a ${this.confirmDialog.user.name}? Esta acción no se puede deshacer.`;
    }

    return this.confirmDialog.user.active
      ? `¿Deseas desactivar el acceso de ${this.confirmDialog.user.name}?`
      : `¿Deseas activar el acceso de ${this.confirmDialog.user.name}?`;
  }

  getConfirmActionLabel(): string {
    if (!this.confirmDialog) {
      return '';
    }

    if (this.confirmDialog.type === 'delete') {
      return this.isDeleteActionLoading(this.confirmDialog.user.id) ? 'Eliminando...' : 'Eliminar';
    }

    return this.confirmDialog.user.active
      ? (this.isStatusActionLoading(this.confirmDialog.user.id) ? 'Desactivando...' : 'Desactivar')
      : (this.isStatusActionLoading(this.confirmDialog.user.id) ? 'Activando...' : 'Activar');
  }

  executeConfirmation(): void {
    if (!this.confirmDialog || this.isProcessingConfirmation) {
      return;
    }

    if (this.confirmDialog.type === 'delete') {
      this.confirmDeleteUser(this.confirmDialog.user);
      return;
    }

    this.confirmToggleStatus(this.confirmDialog.user);
  }

  @HostListener('document:click')
  onDocumentClick(): void {
    if (this.openMenuUserId) {
      this.openMenuUserId = null;
      this.triggerViewUpdate();
    }
  }

  onMenuClick(event: Event): void {
    event.stopPropagation();
  }

  onMenuButtonClick(event: Event, userId: string): void {
    event.stopPropagation();
    this.toggleMenu(userId);
  }

  private isFormValid(): boolean {
    return !this.getNameError() && !this.getEmailError() && !this.getPasswordError();
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

  private isValidEmail(value: string): boolean {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim());
  }
}
