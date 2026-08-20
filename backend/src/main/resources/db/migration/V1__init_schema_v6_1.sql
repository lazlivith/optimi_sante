-- 1. EXTENSION
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 2. MULTI-TENANT & CORE USERS
CREATE TABLE tenants (
   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
   code VARCHAR(50) UNIQUE NOT NULL, -- 'FR_MAIN'
   name VARCHAR(150) NOT NULL,
   domain VARCHAR(255) UNIQUE NOT NULL,
   currency VARCHAR(10) DEFAULT 'EUR',
   created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
   tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
   email VARCHAR(255) UNIQUE NOT NULL,
   password_hash VARCHAR(255) NOT NULL,
   role VARCHAR(30) NOT NULL CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'CLIENT_B2C', 'CLIENT_B2B', 'MEDECIN', 'CENTRE_FORMATION')),
   is_active BOOLEAN DEFAULT TRUE,
   created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
   deleted_at TIMESTAMP WITH TIME ZONE NULL -- Soft Delete
);

-- 3. EXTENSIONS PROFIL (CLASS TABLE INHERITANCE)
CREATE TABLE company_profiles (
   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
   user_id UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
   company_name VARCHAR(255) NOT NULL,
   siret_finess VARCHAR(50) NOT NULL,
   vat_number VARCHAR(50),
   billing_address TEXT NOT NULL,
   b2b_discount_rate DECIMAL(5, 2) DEFAULT 0.00,
   updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE doctor_profiles (
   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
   user_id UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
   first_name VARCHAR(100) NOT NULL,
   last_name VARCHAR(100) NOT NULL,
   phone_whatsapp VARCHAR(30) NOT NULL,
   country_of_residence VARCHAR(100) NOT NULL,
   medical_specialty VARCHAR(150) NOT NULL,
   medical_council_number VARCHAR(100),
   current_hospital VARCHAR(255),
   passport_number VARCHAR(100),
   updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE partner_profiles (
   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
   user_id UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
   institution_name VARCHAR(255) NOT NULL,
   finess_accreditation VARCHAR(100) NOT NULL,
   contact_person_name VARCHAR(150) NOT NULL,
   contact_email VARCHAR(255) NOT NULL,
   contact_phone VARCHAR(30) NOT NULL,
   address TEXT NOT NULL,
   is_verified BOOLEAN DEFAULT FALSE,
   created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. DOMAINE E-COMMERCE & RESERVATION TRANSACTIONNELLE
CREATE TABLE categories (
   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
   tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
   name VARCHAR(150) NOT NULL,
   slug VARCHAR(150) NOT NULL,
   parent_id UUID REFERENCES categories(id) ON DELETE SET NULL,
   UNIQUE(tenant_id, slug)
);

CREATE TABLE products (
   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
   tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
   sku VARCHAR(100) UNIQUE NOT NULL,
   name VARCHAR(255) NOT NULL,
   slug VARCHAR(255) NOT NULL,
   description TEXT,
   base_price DECIMAL(10, 2) NOT NULL,
   stock_quantity INT DEFAULT 0,
   stock_threshold INT DEFAULT 5,
   is_quote_only BOOLEAN DEFAULT FALSE,
   is_active BOOLEAN DEFAULT TRUE,
   category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
   version INT DEFAULT 1, -- Optimistic Locking
   created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
   deleted_at TIMESTAMP WITH TIME ZONE NULL -- Soft Delete
);

CREATE TABLE stock_reservations (
   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
   product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
   user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
   quantity INT NOT NULL,
   expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
   created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE orders (
   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
   tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
   order_number VARCHAR(50) UNIQUE NOT NULL,
   user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
   payment_method VARCHAR(50) CHECK (payment_method IN ('STRIPE_CARD', 'BANK_TRANSFER', 'QUOTE_REQUEST')),
   payment_status VARCHAR(30) DEFAULT 'UNPAID' CHECK (payment_status IN ('UNPAID', 'PAID', 'PENDING_APPROVAL', 'QUOTE_SENT', 'QUOTE_REJECTED')),
   status VARCHAR(30) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROCESSING', 'SHIPPED', 'CANCELLED')),
   is_quote BOOLEAN DEFAULT FALSE,
   total_amount DECIMAL(10, 2) NOT NULL,
   created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_items (
   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
   order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
   product_id UUID REFERENCES products(id),
   unit_price DECIMAL(10, 2) NOT NULL,
   quantity INT NOT NULL,
   subtotal DECIMAL(10, 2) NOT NULL
);

-- 5. DOMAINE FORMATIONS & MOBILITÉ
CREATE TABLE trainings (
   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
   tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
   partner_id UUID NOT NULL REFERENCES partner_profiles(id) ON DELETE RESTRICT,
   title VARCHAR(255) NOT NULL,
   slug VARCHAR(255) UNIQUE NOT NULL,
   medical_specialty VARCHAR(150) NOT NULL,
   description TEXT NOT NULL,
   brochure_s3_key TEXT NOT NULL,
   duration_days INT NOT NULL,
   is_long_stay BOOLEAN DEFAULT FALSE,
   price DECIMAL(10, 2) NOT NULL,
   is_published BOOLEAN DEFAULT FALSE
);

CREATE TABLE training_sessions (
   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
   training_id UUID NOT NULL REFERENCES trainings(id) ON DELETE CASCADE,
   start_date DATE NOT NULL,
   end_date DATE NOT NULL,
   capacity INT NOT NULL,
   available_seats INT NOT NULL,
   location_hospital VARCHAR(255) NOT NULL,
   status VARCHAR(30) DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'FULL', 'CANCELLED', 'COMPLETED'))
);

CREATE TABLE prospect_leads (
   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
   training_id UUID REFERENCES trainings(id) ON DELETE CASCADE,
   email VARCHAR(255) NOT NULL,
   first_name VARCHAR(100) NOT NULL,
   last_name VARCHAR(100) NOT NULL,
   phone_whatsapp VARCHAR(30) NOT NULL,
   country VARCHAR(100) NOT NULL,
   specialty VARCHAR(150) NOT NULL,
   downloaded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE enrollments (
   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
   enrollment_number VARCHAR(50) UNIQUE NOT NULL,
   doctor_id UUID NOT NULL REFERENCES doctor_profiles(id) ON DELETE RESTRICT,
   session_id UUID NOT NULL REFERENCES training_sessions(id) ON DELETE RESTRICT,
   lead_id UUID REFERENCES prospect_leads(id) ON DELETE SET NULL, -- Traçabilité Lead Capture
   status VARCHAR(30) DEFAULT 'PENDING_REVIEW' CHECK (status IN (
        'PENDING_REVIEW', 'APPROVED_ACADEMIC', 'APPROVED_ADMINISTRATIVE',
       'CONVENTION_ISSUED', 'VISA_SUBMITTED', 'READY_TO_START', 'REJECTED'
   )),
   tripartite_convention_s3_key TEXT,
   rejection_reason TEXT,
   created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE enrollment_documents (
   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
   enrollment_id UUID NOT NULL REFERENCES enrollments(id) ON DELETE CASCADE,
   document_type VARCHAR(50) NOT NULL CHECK (document_type IN ('PASSPORT', 'DIPLOMA', 'MEDICAL_COUNCIL_CERT', 'FINANCIAL_GUARANTEE', 'VISA_GRANT')),
   s3_key TEXT NOT NULL,
   is_verified BOOLEAN DEFAULT FALSE,
   uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 6. Index d'Optimisation
CREATE INDEX idx_products_tenant_sku ON products(tenant_id, sku) WHERE deleted_at IS NULL;
CREATE INDEX idx_stock_res_expiry ON stock_reservations(expires_at);
CREATE INDEX idx_orders_user ON orders(user_id);
CREATE INDEX idx_enrollments_doctor ON enrollments(doctor_id);
CREATE INDEX idx_enrollments_lead ON enrollments(lead_id);
