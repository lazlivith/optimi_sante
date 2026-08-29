CREATE TABLE IF NOT EXISTS enrollment_documents (
    id UUID PRIMARY KEY,
    enrollment_id UUID NOT NULL REFERENCES enrollments(id) ON DELETE CASCADE,
    document_type VARCHAR(50) NOT NULL,
    cloudinary_public_id VARCHAR(255),
    file_url VARCHAR(1024),
    is_verified BOOLEAN DEFAULT FALSE,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
