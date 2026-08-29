import { type ReactNode } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { HomePage } from './pages/HomePage';
import { CatalogPage } from './pages/CatalogPage';
import { ProductDetailPage } from './pages/ProductDetailPage';
import { CartPage } from './pages/CartPage';
import { LoginPage } from './pages/auth/LoginPage';
import { RegisterPage } from './pages/auth/RegisterPage';
import { TrainingsPage } from './pages/training/TrainingsPage';
import { QuotesAdminPage } from './pages/admin/QuotesAdminPage';
import { TrainingEnrollmentPage } from './pages/training/TrainingEnrollmentPage';
import { DoctorApplicationPage } from './pages/training/DoctorApplicationPage';
import { CandidatureSuccessPage } from './pages/candidature/CandidatureSuccessPage';
import { CandidatureCancelPage } from './pages/candidature/CandidatureCancelPage';
import { DoctorVaultPage } from './pages/dashboard/DoctorVaultPage';
import { ProfilePage } from './pages/dashboard/ProfilePage';
import { CheckoutPage } from './pages/CheckoutPage';
import { CheckoutSuccessPage } from './pages/checkout/CheckoutSuccessPage';
import { CheckoutCancelPage } from './pages/checkout/CheckoutCancelPage';
import { CheckoutCompletePage } from './pages/checkout/CheckoutCompletePage';
import { TrainingDetailPage } from './pages/training/TrainingDetailPage';
import { MyEnrollmentDetailPage } from './pages/dashboard/MyEnrollmentDetailPage';
import { MyEnrollmentsListPage } from './pages/dashboard/MyEnrollmentsListPage';
import { MyOrdersPage } from './pages/dashboard/MyOrdersPage';
import { AdminEnrollmentDetailPage } from './pages/admin/AdminEnrollmentDetailPage';
import { AdminDashboardPage } from './pages/admin/AdminDashboardPage';
import { AdminUsersPage } from './pages/admin/AdminUsersPage';
import { AdminEnrollmentsListPage } from './pages/admin/AdminEnrollmentsListPage';
import { AdminCatalogPage } from './pages/admin/AdminCatalogPage';
import { AdminPartnershipRequestsPage } from './pages/admin/AdminPartnershipRequestsPage';
import { AdminTrainingsPage } from './pages/admin/AdminTrainingsPage';
import { AdminFinancePage } from './pages/admin/AdminFinancePage';
import { AdminOrdersPage } from './pages/admin/AdminOrdersPage';
import { AdminPromoCodesPage } from './pages/admin/AdminPromoCodesPage';
import { BecomePartnerPage } from './pages/partnership/BecomePartnerPage';
import { PartnerDashboardHomePage } from './pages/partner/PartnerDashboardHomePage';
import { PartnerEnrollmentsPage } from './pages/partner/PartnerEnrollmentsPage';
import { PartnerSessionsPage } from './pages/partner/PartnerSessionsPage';
import { PartnerTrainingsPage } from './pages/partner/PartnerTrainingsPage';
import { AdminLayout } from './layouts/AdminLayout';
import { PartnerLayout } from './layouts/PartnerLayout';
import { DoctorLayout } from './layouts/DoctorLayout';
import { Navbar } from './components/Navbar';
import { ProtectedRoute } from './components/ProtectedRoute';

const Layout = ({ children }: { children: ReactNode }) => {
  return (
    <div className="min-h-screen flex flex-col font-sans bg-gray-50">
      <Navbar />

      <main className="flex-1">
        {children}
      </main>

      <footer className="border-t border-gray-100 text-xs text-gray-500 py-8 mt-4 bg-white">
        <div className="container mx-auto px-6 flex flex-col md:flex-row justify-between items-center gap-4">
          <p>© 2026 Optimi Santé · SAS, Bordeaux</p>
          <div className="flex items-center gap-4">
            <span>Négoce B2B/B2C</span>
            <span>·</span>
            <span>Formations médicales</span>
            <span>·</span>
            <span>Mobilité Afrique → France</span>
          </div>
        </div>
      </footer>
    </div>
  );
};

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Espace Admin : layout dédié (sidebar), pas la navbar boutique */}
        <Route element={<ProtectedRoute allowedRoles={['SUPER_ADMIN', 'ADMIN']} />}>
          <Route path="/admin" element={<AdminLayout />}>
            <Route index element={<AdminDashboardPage />} />
            <Route path="users" element={<AdminUsersPage />} />
            <Route path="catalog" element={<AdminCatalogPage />} />
            <Route path="quotes" element={<QuotesAdminPage />} />
            <Route path="enrollments" element={<AdminEnrollmentsListPage />} />
            <Route path="enrollments/:id" element={<AdminEnrollmentDetailPage />} />
            <Route path="partnership-requests" element={<AdminPartnershipRequestsPage />} />
            <Route path="trainings" element={<AdminTrainingsPage />} />
            <Route path="finance" element={<AdminFinancePage />} />
            <Route path="orders" element={<AdminOrdersPage />} />
            <Route path="promo-codes" element={<AdminPromoCodesPage />} />
          </Route>
        </Route>

        {/* Espace Partenaire (CHU) : layout dédié (sidebar) */}
        <Route element={<ProtectedRoute allowedRoles={['CENTRE_FORMATION']} />}>
          <Route path="/partner" element={<PartnerLayout />}>
            <Route index element={<PartnerDashboardHomePage />} />
            <Route path="trainings" element={<PartnerTrainingsPage />} />
            <Route path="enrollments" element={<PartnerEnrollmentsPage />} />
            <Route path="sessions" element={<PartnerSessionsPage />} />
            <Route path="profile" element={<ProfilePage />} />
          </Route>
        </Route>

        {/* Espace Médecin : layout dédié (sidebar) */}
        <Route element={<ProtectedRoute allowedRoles={['MEDECIN']} />}>
          <Route path="/doctor" element={<DoctorLayout />}>
            <Route index element={<MyEnrollmentsListPage />} />
            <Route path="vault" element={<DoctorVaultPage />} />
            <Route path="profile" element={<ProfilePage />} />
            <Route path="enrollments/:id" element={<MyEnrollmentDetailPage />} />
          </Route>
        </Route>

        {/* Boutique / espaces B2C, B2B : navbar + footer classiques */}
        <Route
          path="*"
          element={
            <Layout>
              <Routes>
                <Route path="/" element={<HomePage />} />
                <Route path="/catalog" element={<CatalogPage />} />
                <Route path="/formations" element={<TrainingsPage />} />
                <Route path="/product/:slug" element={<ProductDetailPage />} />
                <Route path="/cart" element={<CartPage />} />
                <Route path="/checkout" element={<CheckoutPage />} />
                <Route path="/checkout/complete" element={<CheckoutCompletePage />} />
                <Route path="/checkout/success" element={<CheckoutSuccessPage />} />
                <Route path="/checkout/cancel" element={<CheckoutCancelPage />} />
                <Route path="/login" element={<LoginPage />} />
                <Route path="/register" element={<RegisterPage />} />
                <Route path="/formations/:id" element={<TrainingDetailPage />} />
                <Route path="/formations/:id/enroll" element={<TrainingEnrollmentPage />} />
                <Route path="/formations/:id/postuler" element={<DoctorApplicationPage />} />
                <Route path="/candidature/success" element={<CandidatureSuccessPage />} />
                <Route path="/candidature/cancel" element={<CandidatureCancelPage />} />
                <Route path="/devenir-partenaire" element={<BecomePartnerPage />} />

                <Route element={<ProtectedRoute />}>
                  <Route path="/profile" element={<ProfilePage />} />
                  <Route path="/my-orders" element={<MyOrdersPage />} />
                </Route>
              </Routes>
            </Layout>
          }
        />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
