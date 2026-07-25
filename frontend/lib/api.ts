// Thin fetch wrapper. Requests go through Next's /api/* rewrite (see next.config.js),
// which proxies to the Spring Boot backend — so no CORS setup is needed in the browser.

export type Industry = "SALON" | "RETAIL" | "RESTAURANT" | "SCHOOL" | "OTHER";

export interface AuthResponse {
  token: string;
  userId: string;
  businessId: string;
  businessName: string;
  fullName: string;
  email: string;
  role: string;
  mustChangePassword: boolean;
}

export interface RegisterBusinessPayload {
  businessName: string;
  industry: Industry;
  location?: string;
  contactPhone?: string;
  ownerFullName: string;
  email: string;
  password: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export interface BusinessSummary {
  id: string;
  name: string;
  industry: string;
  location: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
  currency: string;
  subscriptionPlan: string;
  enabledModules: string[];
  logoUrl: string | null;
}

export interface UserSummary {
  id: string;
  fullName: string;
  email: string;
  role: string;
  active: boolean;
  commissionRate: number;
  createdAt: string;
}

export type StaffRole = "OWNER" | "MANAGER" | "SALES_PERSON" | "ACCOUNTANT";

export interface CreateStaffPayload {
  fullName: string;
  email: string;
  role: StaffRole;
  temporaryPassword: string;
}

export interface AdminResetPasswordResponse {
  temporaryPassword: string;
}

export interface PlatformAuditLogEntry {
  id: string;
  action: string;
  businessId: string | null;
  businessName: string | null;
  adminName: string;
  createdAt: string;
}

export interface DayCount {
  date: string;
  count: number;
}

export interface PlatformStats {
  totalBusinesses: number;
  activeBusinesses: number;
  totalUsers: number;
  totalPlatformRevenue: number;
  signupsByDay: DayCount[];
  activityByDay: DayCount[];
}

export type MovementType = "ADD" | "REMOVE" | "ADJUST";

export interface Product {
  id: string;
  name: string;
  category: string | null;
  categoryId: string | null;
  sku: string | null;
  costPrice: number;
  sellingPrice: number;
  quantity: number;
  lowStockThreshold: number;
  lowStock: boolean;
  supplierName: string | null;
  imageUrl: string | null;
  active: boolean;
  createdAt: string;
}

export interface ProductPayload {
  name: string;
  category?: string;
  categoryId?: string;
  sku?: string;
  costPrice?: number;
  sellingPrice?: number;
  quantity?: number;
  lowStockThreshold?: number;
  supplierName?: string;
  imageUrl?: string;
}

export interface StockAdjustmentPayload {
  movementType: MovementType;
  quantity: number;
  note?: string;
}

export interface StockMovement {
  id: string;
  movementType: MovementType;
  quantityChange: number;
  resultingQuantity: number;
  note: string | null;
  createdAt: string;
}

export interface Customer {
  id: string;
  fullName: string;
  phone: string | null;
  email: string | null;
  notes: string | null;
  totalSpent: number;
  purchaseCount: number;
  createdAt: string;
}

export interface CustomerPayload {
  fullName: string;
  phone?: string;
  email?: string;
  notes?: string;
}

export type PaymentMethod = "CASH" | "MOBILE_MONEY" | "CARD" | "BANK_TRANSFER";

export interface SaleItemPayload {
  productId: string;
  quantity: number;
  discountAmount?: number;
}

export interface SalePayload {
  customerId?: string;
  paymentMethod: PaymentMethod;
  items: SaleItemPayload[];
}

export interface SaleItem {
  productId: string;
  productName: string;
  unitPrice: number;
  quantity: number;
  discountAmount: number;
  subtotal: number;
}

export interface Sale {
  id: string;
  saleNumber: number;
  customerId: string | null;
  customerName: string | null;
  cashierName: string;
  paymentMethod: PaymentMethod;
  totalAmount: number;
  commissionAmount: number;
  items: SaleItem[];
  createdAt: string;
}

export type ExpenseCategory =
  | "RENT"
  | "UTILITIES"
  | "TRANSPORT"
  | "SUPPLIES"
  | "SALARY"
  | "MARKETING"
  | "OTHER";

export interface Expense {
  id: string;
  category: ExpenseCategory;
  description: string | null;
  amount: number;
  expenseDate: string;
  recordedByName: string;
  createdAt: string;
}

export interface ExpensePayload {
  category: ExpenseCategory;
  description?: string;
  amount: number;
  expenseDate?: string;
}

export interface ReportSummary {
  from: string;
  to: string;
  revenue: number;
  expenses: number;
  profit: number;
  salesCount: number;
  expenseCount: number;
}

export interface GoogleRegisterPayload {
  idToken: string;
  businessName: string;
  industry: Industry;
  location?: string;
  contactPhone?: string;
}

export interface ActivityLogEntry {
  id: string;
  businessId: string;
  businessName: string;
  userId: string | null;
  userName: string;
  action: string;
  entityType: string | null;
  entityId: string | null;
  createdAt: string;
}

export interface PlatformAuthResponse {
  token: string;
  adminId: string;
  fullName: string;
  email: string;
  mustChangePassword: boolean;
}

export interface Supplier {
  id: string;
  name: string;
  phone: string | null;
  email: string | null;
  notes: string | null;
  createdAt: string;
}

export interface SupplierPayload {
  name: string;
  phone?: string;
  email?: string;
  notes?: string;
}

export type PurchaseOrderStatus = "PENDING" | "RECEIVED" | "CANCELLED";

export interface PurchaseOrderItem {
  productId: string;
  productName: string;
  unitCost: number;
  quantity: number;
  subtotal: number;
}

export interface PurchaseOrder {
  id: string;
  poNumber: number;
  supplierId: string | null;
  supplierName: string | null;
  status: PurchaseOrderStatus;
  totalAmount: number;
  createdByName: string;
  items: PurchaseOrderItem[];
  createdAt: string;
  receivedAt: string | null;
}

export interface PurchaseOrderItemPayload {
  productId: string;
  quantity: number;
  unitCost: number;
}

export interface PurchaseOrderPayload {
  supplierId?: string;
  items: PurchaseOrderItemPayload[];
}

export type ServiceOrderStatus = "RECEIVED" | "IN_PROGRESS" | "COMPLETED" | "PICKED_UP" | "CANCELLED";

export interface ServiceType {
  id: string;
  name: string;
  usageCount: number;
  createdAt: string;
}

export interface ServiceTypePayload {
  name: string;
}

export interface ServiceCatalogItem {
  id: string;
  serviceTypeId: string;
  serviceTypeName: string | null;
  name: string;
  price: number;
  active: boolean;
  createdAt: string;
}

export interface ServiceCatalogItemPayload {
  serviceTypeId: string;
  name: string;
  price: number;
}

export interface ServiceOrder {
  id: string;
  orderNumber: number;
  serviceTypeId: string;
  serviceTypeName: string | null;
  status: ServiceOrderStatus;
  customerId: string | null;
  customerName: string | null;
  serviceCatalogId: string | null;
  serviceCatalogName: string | null;
  notes: string | null;
  price: number;
  discountAmount: number;
  assignedStaffId: string | null;
  assignedStaffName: string | null;
  receivedAt: string;
  scheduledAt: string | null;
  pickedUpAt: string | null;
  readyEmailSentAt: string | null;
  createdByName: string;
  createdAt: string;
  updatedAt: string;
}

export interface ServiceOrderPayload {
  serviceTypeId: string;
  customerId?: string;
  serviceCatalogId?: string;
  notes?: string;
  price?: number;
  discountAmount?: number;
  assignedStaffId?: string;
  scheduledAt?: string;
}

export interface ServiceOrderUpdatePayload {
  notes?: string;
  price: number;
  discountAmount?: number;
  assignedStaffId?: string;
  scheduledAt?: string;
}

export interface ServiceOrderPhoto {
  id: string;
  url: string;
  createdAt: string;
}

export interface ServiceOrderReport {
  from: string;
  to: string;
  revenueByType: { serviceTypeId: string; serviceTypeName: string | null; revenue: number }[];
  statusCounts: Record<ServiceOrderStatus, number>;
  avgTurnaroundHours: number;
}

export interface ProductCategory {
  id: string;
  name: string;
  productCount: number;
  createdAt: string;
}

export interface ProductCategoryPayload {
  name: string;
}

export interface BusinessUpdatePayload {
  name: string;
  industry: Industry;
  location?: string;
  contactEmail?: string;
  contactPhone?: string;
}

export interface StaffCommission {
  userId: string;
  userName: string;
  commissionRate: number;
  salesCount: number;
  totalSales: number;
  commissionEarned: number;
}

export interface CreatePlatformAdminPayload {
  fullName: string;
  email: string;
  temporaryPassword: string;
}

export interface PlatformAdminSummary {
  id: string;
  fullName: string;
  email: string;
  createdAt: string;
}

export interface PlatformBusinessSummary {
  id: string;
  name: string;
  industry: string;
  location: string | null;
  subscriptionPlan: string;
  active: boolean;
  userCount: number;
  ownerEmail: string;
  createdAt: string;
}

export interface PlatformBusinessDetail {
  id: string;
  name: string;
  industry: string;
  location: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
  currency: string;
  subscriptionPlan: string;
  enabledModules: string[];
  active: boolean;
  createdAt: string;
  users: UserSummary[];
  productCount: number;
  customerCount: number;
  salesCount: number;
  totalRevenue: number;
  expenseCount: number;
  totalExpenses: number;
}

class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function request<T>(
  path: string,
  options: RequestInit = {},
  // Deprecated/unused — kept only so existing call sites (`api.xxx(session.token, ...)`)
  // across the app still type-check without a mass find-and-replace. Real auth
  // now travels via the httpOnly cookie, forwarded by middleware.ts as a
  // Bearer header when it proxies to the backend; the browser attaches the
  // cookie automatically on this same-origin request.
  _token?: string | null
): Promise<T> {
  const res = await fetch(path, {
    ...options,
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      ...options.headers,
    },
  });

  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new ApiError(res.status, body.error || "Request failed");
  }

  // 204 No Content etc.
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

// Receipts/exports: the httpOnly cookie rides along automatically on this
// same-origin request (via middleware.ts), same as request() above — no
// header to attach by hand anymore. The `token` param is unused/deprecated,
// same reasoning as request().
async function downloadFile(url: string, _token: string, filename: string, openInline = false): Promise<void> {
  const res = await fetch(url, { credentials: "same-origin" });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new ApiError(res.status, body.error || "Download failed");
  }
  const blob = await res.blob();
  const objectUrl = URL.createObjectURL(blob);
  if (openInline) {
    window.open(objectUrl, "_blank");
  } else {
    const a = document.createElement("a");
    a.href = objectUrl;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
  }
  setTimeout(() => URL.revokeObjectURL(objectUrl), 60000);
}

export const api = {
  registerBusiness: (payload: RegisterBusinessPayload) =>
    request<AuthResponse>("/session/register", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  login: (payload: LoginPayload) =>
    request<AuthResponse>("/session/login", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  registerWithGoogle: (payload: GoogleRegisterPayload) =>
    request<AuthResponse>("/session/google-register", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  loginWithGoogle: (idToken: string) =>
    request<AuthResponse>("/session/google-login", {
      method: "POST",
      body: JSON.stringify({ idToken }),
    }),

  forgotPassword: (email: string) =>
    request<void>("/api/auth/forgot-password", {
      method: "POST",
      body: JSON.stringify({ email }),
    }),

  resetPassword: (token: string, newPassword: string) =>
    request<void>("/api/auth/reset-password", {
      method: "POST",
      body: JSON.stringify({ token, newPassword }),
    }),

  changePassword: (token: string, currentPassword: string, newPassword: string) =>
    request<void>(
      "/api/auth/change-password",
      { method: "POST", body: JSON.stringify({ currentPassword, newPassword }) },
      token
    ),

  logout: () => request<void>("/session/logout", { method: "POST" }),

  createStaff: (token: string, payload: CreateStaffPayload) =>
    request<UserSummary>("/api/users", { method: "POST", body: JSON.stringify(payload) }, token),

  updateUserStatus: (token: string, userId: string, active: boolean) =>
    request<UserSummary>(
      `/api/users/${userId}/status`,
      { method: "PATCH", body: JSON.stringify({ active }) },
      token
    ),

  updateUserRole: (token: string, userId: string, role: StaffRole) =>
    request<UserSummary>(
      `/api/users/${userId}/role`,
      { method: "PATCH", body: JSON.stringify({ role }) },
      token
    ),

  updateCommissionRate: (token: string, userId: string, rate: number) =>
    request<UserSummary>(
      `/api/users/${userId}/commission-rate`,
      { method: "PATCH", body: JSON.stringify({ rate }) },
      token
    ),

  getMyBusiness: (token: string) =>
    request<BusinessSummary>("/api/business/me", {}, token),

  updateBusinessProfile: (token: string, payload: BusinessUpdatePayload) =>
    request<BusinessSummary>(
      "/api/business/me",
      { method: "PUT", body: JSON.stringify(payload) },
      token
    ),

  uploadBusinessLogo: async (_token: string, file: File): Promise<BusinessSummary> => {
    const formData = new FormData();
    formData.append("file", file);
    const res = await fetch("/api/business/logo", {
      method: "POST",
      credentials: "same-origin",
      body: formData,
    });
    if (!res.ok) {
      const body = await res.json().catch(() => ({ error: res.statusText }));
      throw new ApiError(res.status, body.error || "Upload failed");
    }
    return res.json();
  },

  listUsers: (token: string) => request<UserSummary[]>("/api/users", {}, token),

  listProducts: (token: string, filters?: { search?: string; categoryId?: string; includeArchived?: boolean }) => {
    const params = new URLSearchParams();
    if (filters?.search) params.set("search", filters.search);
    if (filters?.categoryId) params.set("categoryId", filters.categoryId);
    if (filters?.includeArchived) params.set("includeArchived", "true");
    const qs = params.toString();
    return request<Product[]>(`/api/products${qs ? `?${qs}` : ""}`, {}, token);
  },

  listLowStockProducts: (token: string) =>
    request<Product[]>("/api/products/low-stock", {}, token),

  createProduct: (token: string, payload: ProductPayload) =>
    request<Product>(
      "/api/products",
      { method: "POST", body: JSON.stringify(payload) },
      token
    ),

  updateProduct: (token: string, id: string, payload: ProductPayload) =>
    request<Product>(
      `/api/products/${id}`,
      { method: "PUT", body: JSON.stringify(payload) },
      token
    ),

  archiveProduct: (token: string, id: string) =>
    request<Product>(`/api/products/${id}/archive`, { method: "PATCH" }, token),

  restoreProduct: (token: string, id: string) =>
    request<Product>(`/api/products/${id}/restore`, { method: "PATCH" }, token),

  adjustStock: (token: string, id: string, payload: StockAdjustmentPayload) =>
    request<Product>(
      `/api/products/${id}/stock`,
      { method: "POST", body: JSON.stringify(payload) },
      token
    ),

  stockHistory: (token: string, id: string) =>
    request<StockMovement[]>(`/api/products/${id}/stock-history`, {}, token),

  listProductCategories: (token: string) => request<ProductCategory[]>("/api/product-categories", {}, token),

  createProductCategory: (token: string, payload: ProductCategoryPayload) =>
    request<ProductCategory>("/api/product-categories", { method: "POST", body: JSON.stringify(payload) }, token),

  renameProductCategory: (token: string, id: string, payload: ProductCategoryPayload) =>
    request<ProductCategory>(`/api/product-categories/${id}`, { method: "PUT", body: JSON.stringify(payload) }, token),

  deleteProductCategory: (token: string, id: string) =>
    request<void>(`/api/product-categories/${id}`, { method: "DELETE" }, token),

  listServiceOrders: (token: string, filters?: { serviceTypeId?: string; status?: ServiceOrderStatus }) => {
    const params = new URLSearchParams();
    if (filters?.serviceTypeId) params.set("serviceTypeId", filters.serviceTypeId);
    if (filters?.status) params.set("status", filters.status);
    const qs = params.toString();
    return request<ServiceOrder[]>(`/api/service-orders${qs ? `?${qs}` : ""}`, {}, token);
  },

  listServiceTypes: (token: string) => request<ServiceType[]>("/api/service-types", {}, token),

  createServiceType: (token: string, payload: ServiceTypePayload) =>
    request<ServiceType>("/api/service-types", { method: "POST", body: JSON.stringify(payload) }, token),

  renameServiceType: (token: string, id: string, payload: ServiceTypePayload) =>
    request<ServiceType>(`/api/service-types/${id}`, { method: "PUT", body: JSON.stringify(payload) }, token),

  deleteServiceType: (token: string, id: string) =>
    request<void>(`/api/service-types/${id}`, { method: "DELETE" }, token),

  getServiceOrder: (token: string, id: string) => request<ServiceOrder>(`/api/service-orders/${id}`, {}, token),

  createServiceOrder: (token: string, payload: ServiceOrderPayload) =>
    request<ServiceOrder>("/api/service-orders", { method: "POST", body: JSON.stringify(payload) }, token),

  updateServiceOrder: (token: string, id: string, payload: ServiceOrderUpdatePayload) =>
    request<ServiceOrder>(`/api/service-orders/${id}`, { method: "PATCH", body: JSON.stringify(payload) }, token),

  updateServiceOrderStatus: (token: string, id: string, status: ServiceOrderStatus) =>
    request<ServiceOrder>(`/api/service-orders/${id}/status`, { method: "PATCH", body: JSON.stringify({ status }) }, token),

  resendServiceOrderReadyEmail: (token: string, id: string) =>
    request<ServiceOrder>(`/api/service-orders/${id}/resend-ready-email`, { method: "POST" }, token),

  listServiceOrderPhotos: (token: string, orderId: string) =>
    request<ServiceOrderPhoto[]>(`/api/service-orders/${orderId}/photos`, {}, token),

  uploadServiceOrderPhoto: async (_token: string, orderId: string, file: File): Promise<ServiceOrderPhoto> => {
    const formData = new FormData();
    formData.append("file", file);
    const res = await fetch(`/api/service-orders/${orderId}/photos`, {
      method: "POST",
      credentials: "same-origin",
      body: formData,
    });
    if (!res.ok) {
      const body = await res.json().catch(() => ({ error: res.statusText }));
      throw new ApiError(res.status, body.error || "Upload failed");
    }
    return res.json();
  },

  deleteServiceOrderPhoto: (token: string, orderId: string, photoId: string) =>
    request<void>(`/api/service-orders/${orderId}/photos/${photoId}`, { method: "DELETE" }, token),

  getServiceOrderReport: (token: string, from?: string, to?: string) => {
    const params = new URLSearchParams();
    if (from) params.set("from", from);
    if (to) params.set("to", to);
    const qs = params.toString();
    return request<ServiceOrderReport>(`/api/service-orders/report${qs ? `?${qs}` : ""}`, {}, token);
  },

  listServiceCatalog: (token: string, activeOnly?: boolean) =>
    request<ServiceCatalogItem[]>(`/api/service-catalog${activeOnly ? "?activeOnly=true" : ""}`, {}, token),

  createServiceCatalogItem: (token: string, payload: ServiceCatalogItemPayload) =>
    request<ServiceCatalogItem>("/api/service-catalog", { method: "POST", body: JSON.stringify(payload) }, token),

  updateServiceCatalogItem: (token: string, id: string, payload: ServiceCatalogItemPayload) =>
    request<ServiceCatalogItem>(`/api/service-catalog/${id}`, { method: "PUT", body: JSON.stringify(payload) }, token),

  setServiceCatalogItemActive: (token: string, id: string, active: boolean) =>
    request<ServiceCatalogItem>(`/api/service-catalog/${id}/active`, { method: "PATCH", body: JSON.stringify({ active }) }, token),

  listCustomers: (token: string) => request<Customer[]>("/api/customers", {}, token),

  getCustomer: (token: string, id: string) => request<Customer>(`/api/customers/${id}`, {}, token),

  createCustomer: (token: string, payload: CustomerPayload) =>
    request<Customer>(
      "/api/customers",
      { method: "POST", body: JSON.stringify(payload) },
      token
    ),

  listSales: (token: string) => request<Sale[]>("/api/sales", {}, token),

  getSale: (token: string, id: string) => request<Sale>(`/api/sales/${id}`, {}, token),

  createSale: (token: string, payload: SalePayload) =>
    request<Sale>("/api/sales", { method: "POST", body: JSON.stringify(payload) }, token),

  listExpenses: (token: string) => request<Expense[]>("/api/expenses", {}, token),

  createExpense: (token: string, payload: ExpensePayload) =>
    request<Expense>(
      "/api/expenses",
      { method: "POST", body: JSON.stringify(payload) },
      token
    ),

  getReportSummary: (token: string, from?: string, to?: string) => {
    const params = new URLSearchParams();
    if (from) params.set("from", from);
    if (to) params.set("to", to);
    const qs = params.toString();
    return request<ReportSummary>(`/api/reports/summary${qs ? `?${qs}` : ""}`, {}, token);
  },

  getCommissions: (token: string, from?: string, to?: string) => {
    const params = new URLSearchParams();
    if (from) params.set("from", from);
    if (to) params.set("to", to);
    const qs = params.toString();
    return request<StaffCommission[]>(`/api/reports/commissions${qs ? `?${qs}` : ""}`, {}, token);
  },

  downloadReportExport: async (token: string, from: string, to: string) => {
    await downloadFile(`/api/reports/export?from=${from}&to=${to}`, token, `report-${from}-to-${to}.xlsx`);
  },

  downloadReceipt: async (token: string, saleId: string, saleNumber: number) => {
    await downloadFile(`/api/sales/${saleId}/receipt`, token, `receipt-${saleNumber}.pdf`, true);
  },

  listSuppliers: (token: string) => request<Supplier[]>("/api/suppliers", {}, token),

  createSupplier: (token: string, payload: SupplierPayload) =>
    request<Supplier>("/api/suppliers", { method: "POST", body: JSON.stringify(payload) }, token),

  listPurchaseOrders: (token: string) => request<PurchaseOrder[]>("/api/purchase-orders", {}, token),

  createPurchaseOrder: (token: string, payload: PurchaseOrderPayload) =>
    request<PurchaseOrder>(
      "/api/purchase-orders",
      { method: "POST", body: JSON.stringify(payload) },
      token
    ),

  receivePurchaseOrder: (token: string, id: string) =>
    request<PurchaseOrder>(`/api/purchase-orders/${id}/receive`, { method: "POST" }, token),

  cancelPurchaseOrder: (token: string, id: string) =>
    request<PurchaseOrder>(`/api/purchase-orders/${id}/cancel`, { method: "POST" }, token),

  listActivityLogs: (token: string, filters?: { userId?: string; from?: string; to?: string }) => {
    const params = new URLSearchParams();
    if (filters?.userId) params.set("userId", filters.userId);
    if (filters?.from) params.set("from", filters.from);
    if (filters?.to) params.set("to", filters.to);
    const qs = params.toString();
    return request<ActivityLogEntry[]>(`/api/activity-logs${qs ? `?${qs}` : ""}`, {}, token);
  },

  // --- Platform (Super Admin) ---

  platformLogin: (email: string, password: string) =>
    request<PlatformAuthResponse>("/session/platform-login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    }),

  platformLogout: () => request<void>("/session/platform-logout", { method: "POST" }),

  platformForgotPassword: (email: string) =>
    request<void>("/api/platform/auth/forgot-password", {
      method: "POST",
      body: JSON.stringify({ email }),
    }),

  platformResetPassword: (token: string, newPassword: string) =>
    request<void>("/api/platform/auth/reset-password", {
      method: "POST",
      body: JSON.stringify({ token, newPassword }),
    }),

  platformChangePassword: (token: string, currentPassword: string, newPassword: string) =>
    request<void>(
      "/api/platform/auth/change-password",
      { method: "POST", body: JSON.stringify({ currentPassword, newPassword }) },
      token
    ),

  listPlatformAdmins: (token: string) => request<PlatformAdminSummary[]>("/api/platform/admins", {}, token),

  createPlatformAdmin: (token: string, payload: CreatePlatformAdminPayload) =>
    request<PlatformAdminSummary>(
      "/api/platform/admins",
      { method: "POST", body: JSON.stringify(payload) },
      token
    ),

  listPlatformBusinesses: (token: string, query?: string, active?: boolean) => {
    const params = new URLSearchParams();
    if (query) params.set("query", query);
    if (active !== undefined) params.set("active", String(active));
    const qs = params.toString();
    return request<PlatformBusinessSummary[]>(`/api/platform/businesses${qs ? `?${qs}` : ""}`, {}, token);
  },

  getPlatformBusiness: (token: string, id: string) =>
    request<PlatformBusinessDetail>(`/api/platform/businesses/${id}`, {}, token),

  setPlatformBusinessStatus: (token: string, id: string, active: boolean) =>
    request<PlatformBusinessSummary>(
      `/api/platform/businesses/${id}/status`,
      { method: "PATCH", body: JSON.stringify({ active }) },
      token
    ),

  deletePlatformBusiness: (token: string, id: string) =>
    request<void>(`/api/platform/businesses/${id}`, { method: "DELETE" }, token),

  resetPlatformUserPassword: (token: string, businessId: string, userId: string) =>
    request<AdminResetPasswordResponse>(
      `/api/platform/businesses/${businessId}/users/${userId}/reset-password`,
      { method: "POST" },
      token
    ),

  listPlatformActivityLogs: (
    token: string,
    filters?: { businessId?: string; userId?: string; from?: string; to?: string }
  ) => {
    const params = new URLSearchParams();
    if (filters?.businessId) params.set("businessId", filters.businessId);
    if (filters?.userId) params.set("userId", filters.userId);
    if (filters?.from) params.set("from", filters.from);
    if (filters?.to) params.set("to", filters.to);
    const qs = params.toString();
    return request<ActivityLogEntry[]>(`/api/platform/activity-logs${qs ? `?${qs}` : ""}`, {}, token);
  },

  listPlatformAuditLogs: (token: string) =>
    request<PlatformAuditLogEntry[]>("/api/platform/audit-logs", {}, token),

  getPlatformStats: (token: string) => request<PlatformStats>("/api/platform/stats", {}, token),
};

export { ApiError };
