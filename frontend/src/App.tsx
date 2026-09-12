import { type ReactNode, Suspense, lazy } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { Navbar } from './components/Navbar';
import { Footer } from './components/Footer';
import { ProtectedRoute } from './components/ProtectedRoute';
import { PageTransition } from './components/common/PageTransition';
import { ChatWidget } from './components/ai/ChatWidget';
import { ScrollManager } from './components/common/ScrollManager';
// Rôles autorisés par univers, lus de la même définition que la sidebar : le layout n'est
// accessible qu'aux administrateurs, chaque route interne restreint ensuite au métier
// concerné, pour qu'un admin négoce tombe sur un refus explicite s'il vise une URL de mobilité.
import {
  ALL_ADMIN_ROLES, ECOM_ROLES, MOB_ROLES, GOV_ROLES,
} from './lib/adminUniverses';

// Découpage du bundle par route (code-splitting) : chaque page n'est chargée par le
// navigateur qu'au moment où elle est visitée, au lieu d'un seul gros bundle initial.
// Purement une question de performance de chargement — aucune logique métier n'est modifiée.
const HomePage = lazy(() => import('./pages/HomePage').then(m => ({ default: m.HomePage })));
const CatalogPage = lazy(() => import('./pages/CatalogPage').then(m => ({ default: m.CatalogPage })));
const ProductDetailPage = lazy(() => import('./pages/ProductDetailPage').then(m => ({ default: m.ProductDetailPage })));
const CartPage = lazy(() => import('./pages/CartPage').then(m => ({ default: m.CartPage })));
const LoginPage = lazy(() => import('./pages/auth/LoginPage').then(m => ({ default: m.LoginPage })));
const RegisterPage = lazy(() => import('./pages/auth/RegisterPage').then(m => ({ default: m.RegisterPage })));
const TrainingsPage = lazy(() => import('./pages/training/TrainingsPage').then(m => ({ default: m.TrainingsPage })));
const QuotesAdminPage = lazy(() => import('./pages/admin/QuotesAdminPage').then(m => ({ default: m.QuotesAdminPage })));
const TrainingEnrollmentPage = lazy(() => import('./pages/training/TrainingEnrollmentPage').then(m => ({ default: m.TrainingEnrollmentPage })));
const DoctorApplicationPage = lazy(() => import('./pages/training/DoctorApplicationPage').then(m => ({ default: m.DoctorApplicationPage })));
const CandidatureSuccessPage = lazy(() => import('./pages/candidature/CandidatureSuccessPage').then(m => ({ default: m.CandidatureSuccessPage })));
const CandidatureCancelPage = lazy(() => import('./pages/candidature/CandidatureCancelPage').then(m => ({ default: m.CandidatureCancelPage })));
const DoctorVaultPage = lazy(() => import('./pages/dashboard/DoctorVaultPage').then(m => ({ default: m.DoctorVaultPage })));
const ProfilePage = lazy(() => import('./pages/dashboard/ProfilePage').then(m => ({ default: m.ProfilePage })));
const CheckoutPage = lazy(() => import('./pages/CheckoutPage').then(m => ({ default: m.CheckoutPage })));
const CheckoutSuccessPage = lazy(() => import('./pages/checkout/CheckoutSuccessPage').then(m => ({ default: m.CheckoutSuccessPage })));
const CheckoutCancelPage = lazy(() => import('./pages/checkout/CheckoutCancelPage').then(m => ({ default: m.CheckoutCancelPage })));
const CheckoutCompletePage = lazy(() => import('./pages/checkout/CheckoutCompletePage').then(m => ({ default: m.CheckoutCompletePage })));
const TrainingDetailPage = lazy(() => import('./pages/training/TrainingDetailPage').then(m => ({ default: m.TrainingDetailPage })));
const MyEnrollmentDetailPage = lazy(() => import('./pages/dashboard/MyEnrollmentDetailPage').then(m => ({ default: m.MyEnrollmentDetailPage })));
const MyEnrollmentsListPage = lazy(() => import('./pages/dashboard/MyEnrollmentsListPage').then(m => ({ default: m.MyEnrollmentsListPage })));
const MyOrdersPage = lazy(() => import('./pages/dashboard/MyOrdersPage').then(m => ({ default: m.MyOrdersPage })));
const MyPersonalDataPage = lazy(() => import('./pages/dashboard/MyPersonalDataPage').then(m => ({ default: m.MyPersonalDataPage })));
const AdminEnrollmentDetailPage = lazy(() => import('./pages/admin/AdminEnrollmentDetailPage').then(m => ({ default: m.AdminEnrollmentDetailPage })));
const AdminSalesDashboardPage = lazy(() => import('./pages/admin/AdminSalesDashboardPage').then(m => ({ default: m.AdminSalesDashboardPage })));
const AdminMobilityDashboardPage = lazy(() => import('./pages/admin/AdminMobilityDashboardPage').then(m => ({ default: m.AdminMobilityDashboardPage })));
const AdminHomeRedirect = lazy(() => import('./pages/admin/AdminHomeRedirect').then(m => ({ default: m.AdminHomeRedirect })));
const AdminUsersPage = lazy(() => import('./pages/admin/AdminUsersPage').then(m => ({ default: m.AdminUsersPage })));
const AdminEnrollmentsListPage = lazy(() => import('./pages/admin/AdminEnrollmentsListPage').then(m => ({ default: m.AdminEnrollmentsListPage })));
const AdminCatalogPage = lazy(() => import('./pages/admin/AdminCatalogPage').then(m => ({ default: m.AdminCatalogPage })));
const AdminPartnershipRequestsPage = lazy(() => import('./pages/admin/AdminPartnershipRequestsPage').then(m => ({ default: m.AdminPartnershipRequestsPage })));
const AdminTrainingsPage = lazy(() => import('./pages/admin/AdminTrainingsPage').then(m => ({ default: m.AdminTrainingsPage })));
const AdminFinancePage = lazy(() => import('./pages/admin/AdminFinancePage').then(m => ({ default: m.AdminFinancePage })));
const AdminOrdersPage = lazy(() => import('./pages/admin/AdminOrdersPage').then(m => ({ default: m.AdminOrdersPage })));
const AdminPromoCodesPage = lazy(() => import('./pages/admin/AdminPromoCodesPage').then(m => ({ default: m.AdminPromoCodesPage })));
const AdminEmailsPage = lazy(() => import('./pages/admin/AdminEmailsPage').then(m => ({ default: m.AdminEmailsPage })));
const AdminPayoutsPage = lazy(() => import('./pages/admin/AdminPayoutsPage').then(m => ({ default: m.AdminPayoutsPage })));
const BecomePartnerPage = lazy(() => import('./pages/partnership/BecomePartnerPage').then(m => ({ default: m.BecomePartnerPage })));
const ServicesPage = lazy(() => import('./pages/ServicesPage').then(m => ({ default: m.ServicesPage })));
const LegalNoticePage = lazy(() => import('./pages/legal/LegalNoticePage').then(m => ({ default: m.LegalNoticePage })));
const TermsPage = lazy(() => import('./pages/legal/TermsPage').then(m => ({ default: m.TermsPage })));
const PrivacyPolicyPage = lazy(() => import('./pages/legal/PrivacyPolicyPage').then(m => ({ default: m.PrivacyPolicyPage })));
const PartnerDashboardHomePage = lazy(() => import('./pages/partner/PartnerDashboardHomePage').then(m => ({ default: m.PartnerDashboardHomePage })));
const PartnerEnrollmentsPage = lazy(() => import('./pages/partner/PartnerEnrollmentsPage').then(m => ({ default: m.PartnerEnrollmentsPage })));
const PartnerSessionsPage = lazy(() => import('./pages/partner/PartnerSessionsPage').then(m => ({ default: m.PartnerSessionsPage })));
const PartnerTrainingsPage = lazy(() => import('./pages/partner/PartnerTrainingsPage').then(m => ({ default: m.PartnerTrainingsPage })));
const AdminAnalyticsPage = lazy(() => import('./pages/admin/AdminAnalyticsPage').then(m => ({ default: m.AdminAnalyticsPage })));
const AdminReportsPage = lazy(() => import('./pages/admin/AdminReportsPage').then(m => ({ default: m.AdminReportsPage })));
const AdminAuditLogPage = lazy(() => import('./pages/admin/AdminAuditLogPage').then(m => ({ default: m.AdminAuditLogPage })));
const AdminGovernancePage = lazy(() => import('./pages/admin/AdminGovernancePage').then(m => ({ default: m.AdminGovernancePage })));
const AdminAlertsPage = lazy(() => import('./pages/admin/AdminAlertsPage').then(m => ({ default: m.AdminAlertsPage })));
const AdminAiPage = lazy(() => import('./pages/admin/AdminAiPage').then(m => ({ default: m.AdminAiPage })));
const NotificationsPage = lazy(() => import('./pages/NotificationsPage').then(m => ({ default: m.NotificationsPage })));
const NotificationSettingsPage = lazy(() => import('./pages/NotificationSettingsPage').then(m => ({ default: m.NotificationSettingsPage })));
const PartnerFinancePage = lazy(() => import('./pages/partner/PartnerFinancePage').then(m => ({ default: m.PartnerFinancePage })));
const AdminLayout = lazy(() => import('./layouts/AdminLayout').then(m => ({ default: m.AdminLayout })));
const PartnerLayout = lazy(() => import('./layouts/PartnerLayout').then(m => ({ default: m.PartnerLayout })));
const DoctorLayout = lazy(() => import('./layouts/DoctorLayout').then(m => ({ default: m.DoctorLayout })));

const PageFallback = () => (
  <div className="flex items-center justify-center min-h-[60vh]">
    <div className="h-8 w-8 rounded-full border-2 border-brand border-t-transparent animate-spin" />
  </div>
);

const Layout = ({ children }: { children: ReactNode }) => {
  return (
    <div className="min-h-screen flex flex-col font-sans bg-gray-50">
      <Navbar />

      <main className="flex-1">
        <PageTransition>{children}</PageTransition>
      </main>

      <Footer />
    </div>
  );
};

export function App() {
  return (
    <BrowserRouter>
      {/* Doit vivre DANS le routeur : il lit l'adresse courante pour atteindre l'ancre
          demandée et remettre le défilement en haut lors d'une navigation ordinaire. */}
      <ScrollManager />
      <Suspense fallback={<PageFallback />}>
        <Routes>
            <Route element={<ProtectedRoute allowedRoles={ALL_ADMIN_ROLES} />}>
            <Route path="/admin" element={<AdminLayout />}>
              {/* L'accueil /admin renvoie chacun vers le tableau de bord de son métier. */}
              <Route index element={<AdminHomeRedirect />} />

              {/* --- Univers Négoce --- */}
              <Route element={<ProtectedRoute allowedRoles={ECOM_ROLES} />}>
                <Route path="ventes" element={<AdminSalesDashboardPage />} />
                <Route path="finance" element={<AdminFinancePage />} />
                <Route path="orders" element={<AdminOrdersPage />} />
                <Route path="catalog" element={<AdminCatalogPage />} />
                <Route path="promo-codes" element={<AdminPromoCodesPage />} />
                <Route path="quotes" element={<QuotesAdminPage />} />
              </Route>

              {/* --- Univers Mobilité --- */}
              <Route element={<ProtectedRoute allowedRoles={MOB_ROLES} />}>
                <Route path="mobilite" element={<AdminMobilityDashboardPage />} />
                <Route path="enrollments" element={<AdminEnrollmentsListPage />} />
                <Route path="enrollments/:id" element={<AdminEnrollmentDetailPage />} />
                <Route path="trainings" element={<AdminTrainingsPage />} />
                <Route path="partnership-requests" element={<AdminPartnershipRequestsPage />} />
                <Route path="payouts" element={<AdminPayoutsPage />} />
              </Route>

              {/* --- Supervision : transverse aux deux métiers --- */}
              <Route element={<ProtectedRoute allowedRoles={ALL_ADMIN_ROLES} />}>
                <Route path="emails" element={<AdminEmailsPage />} />
                <Route path="alerts" element={<AdminAlertsPage />} />
                <Route path="ai" element={<AdminAiPage />} />
              </Route>

              {/* --- Gouvernance --- */}
              <Route element={<ProtectedRoute allowedRoles={GOV_ROLES} />}>
                <Route path="users" element={<AdminUsersPage />} />
                <Route path="analytics" element={<AdminAnalyticsPage />} />
                <Route path="reports" element={<AdminReportsPage />} />
                <Route path="audit" element={<AdminAuditLogPage />} />
                <Route path="governance" element={<AdminGovernancePage />} />
              </Route>
            </Route>
          </Route>

          {/* Espace Partenaire (CHU) : layout dédié (sidebar) */}
          <Route element={<ProtectedRoute allowedRoles={['CENTRE_FORMATION']} />}>
            <Route path="/partner" element={<PartnerLayout />}>
              <Route index element={<PartnerDashboardHomePage />} />
              <Route path="trainings" element={<PartnerTrainingsPage />} />
              <Route path="enrollments" element={<PartnerEnrollmentsPage />} />
              <Route path="sessions" element={<PartnerSessionsPage />} />
              <Route path="finance" element={<PartnerFinancePage />} />
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

                  {/* Pages juridiques : publiques par obligation — elles doivent etre
                      consultables sans compte, y compris par un visiteur qui hesite. */}
                  {/* Page de presentation, consultable avant tout engagement. */}
                  <Route path="/services" element={<ServicesPage />} />

                  <Route path="/mentions-legales" element={<LegalNoticePage />} />
                  <Route path="/cgv" element={<TermsPage />} />
                  <Route path="/politique-confidentialite" element={<PrivacyPolicyPage />} />

                  <Route element={<ProtectedRoute />}>
                    <Route path="/profile" element={<ProfilePage />} />
                    <Route path="/my-orders" element={<MyOrdersPage />} />
                    <Route path="/notifications" element={<NotificationsPage />} />
                    <Route path="/notifications/settings" element={<NotificationSettingsPage />} />
                    <Route path="/mes-donnees" element={<MyPersonalDataPage />} />
                  </Route>
                </Routes>
              </Layout>
            }
          />
        </Routes>
      </Suspense>

      {/* Assistant conversationnel — disponible sur tous les espaces, y compris hors connexion
          (catalogue et formations uniquement dans ce cas). */}
      <ChatWidget />
    </BrowserRouter>
  );
}

export default App;
