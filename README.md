# AI TrueJobs - AI-Powered Job Platform

An intelligent job matching platform that uses AI to connect job seekers with perfect opportunities through advanced ATS scoring, resume analysis, and smart recommendations.

## 🚀 Features

### For Job Seekers
- **Smart Job Matching**: AI-powered job recommendations based on your skills and experience
- **ATS Score Analysis**: Get instant feedback on how your resume matches job requirements
- **Resume Optimization**: AI-generated suggestions to improve your resume
- **Application Tracking**: Monitor your application status with detailed feedback

### For Recruiters
- **AI Job Creation**: Generate professional job descriptions from simple prompts
- **Candidate Scoring**: Automatic ATS scoring for incoming applications
- **Smart Filtering**: Find the best candidates using AI-powered matching
- **Feedback Generation**: Auto-generated rejection reasons and improvement suggestions

## 🛠️ Tech Stack

### Backend
- **Java 17** with **Spring Boot 3.2.0**
- **Spring Security** for authentication (JWT)
- **Spring Data JPA** with H2/PostgreSQL
- **OpenAI API** for AI features
- **Gradle** for build management

### Frontend
- **Next.js 14** with **TypeScript**
- **Tailwind CSS** for styling
- **Zustand** for state management
- **React Hook Form** with **Yup** validation
- **Framer Motion** for animations
- **Axios** for API calls

## 📋 Prerequisites

- **Java 17+**
- **Node.js 18+**
- **npm or yarn**
- **OpenAI API Key**

## 🚀 Quick Start

### 1. Clone the Repository
```bash
git clone https://github.com/yourusername/ai-truejobs.git
cd ai-truejobs
```

### 2. Backend Setup

```bash
cd backend

# Configure environment variables
cp src/main/resources/application.properties.example src/main/resources/application.properties

# Add your OpenAI API key
echo "openai.api.key=your-openai-api-key" >> src/main/resources/application.properties

# Run the application
./gradlew bootRun
```

The backend will start on `http://localhost:8080`

### 3. Frontend Setup

```bash
cd frontend

# Install dependencies
npm install

# Configure environment variables
cp .env.local.example .env.local

# Add your API URLs and keys
echo "NEXT_PUBLIC_API_URL=http://localhost:8080/api" >> .env.local
echo "NEXT_PUBLIC_OPENAI_API_KEY=your-openai-api-key" >> .env.local

# Start the development server
npm run dev
```

The frontend will start on `http://localhost:3000`

## 🗄️ Database Schema

### Core Entities

```sql
-- Users table
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role ENUM('CANDIDATE', 'RECRUITER', 'ADMIN') NOT NULL,
    photo_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Jobs table
CREATE TABLE jobs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    requirements TEXT,
    skills TEXT,
    location VARCHAR(255),
    salary_range VARCHAR(100),
    job_type VARCHAR(50),
    is_published BOOLEAN DEFAULT FALSE,
    created_by BIGINT NOT NULL,
    embedding_vector TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id)
);

-- Resumes table
CREATE TABLE resumes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_url VARCHAR(500) NOT NULL,
    file_type VARCHAR(50),
    file_size BIGINT,
    extracted_text TEXT,
    embedding_vector TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Applications table
CREATE TABLE applications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL,
    resume_id BIGINT NOT NULL,
    ats_score DECIMAL(5,2),
    status ENUM('PENDING', 'REVIEWED', 'SHORTLISTED', 'REJECTED', 'HIRED') DEFAULT 'PENDING',
    feedback TEXT,
    rejection_reason TEXT,
    cover_letter TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (job_id) REFERENCES jobs(id),
    FOREIGN KEY (resume_id) REFERENCES resumes(id),
    UNIQUE KEY unique_application (user_id, job_id)
);
```

## 🤖 AI Features

### 1. ATS Scoring
Uses OpenAI embeddings to calculate similarity between resumes and job descriptions:

```java
public Mono<Double> calculateATSScore(String resumeText, String jobDescription) {
    return Mono.zip(
        generateEmbedding(resumeText),
        generateEmbedding(jobDescription)
    ).map(tuple -> {
        List<Double> resumeEmbedding = tuple.getT1();
        List<Double> jobEmbedding = tuple.getT2();
        
        double similarity = calculateCosineSimilarity(resumeEmbedding, jobEmbedding);
        return Math.max(0, Math.min(100, similarity * 100));
    });
}
```

### 2. Job Description Generation
AI-powered job description creation from simple prompts:

```java
public Mono<String> generateJobDescription(String jobTitle, String company, String requirements) {
    String prompt = String.format(
        "Create a professional job description for the position of %s at %s. " +
        "Requirements: %s. Include job summary, key responsibilities, qualifications, and benefits.",
        jobTitle, company, requirements
    );
    
    return generateChatCompletion(prompt);
}
```

### 3. Resume Analysis
Detailed feedback on resume-job compatibility:

```java
public Mono<String> generateATSFeedback(String resumeText, String jobDescription, double score) {
    String prompt = String.format(
        "Analyze this resume against the job description and provide constructive ATS feedback. " +
        "ATS Score: %.1f/100. Provide specific feedback on skills match and improvements.",
        score
    );
    
    return generateChatCompletion(prompt);
}
```

## 📚 API Documentation

### Authentication Endpoints
```
POST /api/auth/register - Register new user
POST /api/auth/login    - User login
POST /api/auth/logout   - User logout
GET  /api/auth/me       - Get current user
```

### Jobs Endpoints
```
GET    /api/jobs              - Get all jobs (with filters)
GET    /api/jobs/{id}         - Get job by ID
POST   /api/jobs              - Create new job (recruiter only)
PUT    /api/jobs/{id}         - Update job (recruiter only)
DELETE /api/jobs/{id}         - Delete job (recruiter only)
POST   /api/jobs/generate     - AI job generation
```

### Applications Endpoints
```
GET  /api/applications                    - Get user applications
POST /api/applications/jobs/{id}/apply    - Apply to job
PUT  /api/applications/{id}/status        - Update application status
GET  /api/applications/recruiter/{id}     - Get recruiter applications
```

### Resumes Endpoints
```
GET    /api/resumes        - Get user resumes
POST   /api/resumes/upload - Upload resume
DELETE /api/resumes/{id}   - Delete resume
POST   /api/resumes/analyze - Analyze resume vs job
```

## 🎨 Frontend Components

### Key Pages
- **Homepage**: Landing page with features overview
- **Authentication**: Login/Register with role selection
- **Jobs Listing**: Searchable job listings with filters
- **Job Details**: Detailed job view with application
- **Dashboard**: User dashboard (candidate/recruiter views)
- **Applications**: Application tracking and management

### Responsive Design
- Mobile-first approach with Tailwind CSS
- Responsive navigation and layouts
- Touch-friendly interfaces
- Progressive enhancement

## 🔧 Development

### Backend Development
```bash
# Run tests
./gradlew test

# Build application
./gradlew build

# Run with specific profile
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### Frontend Development
```bash
# Development server
npm run dev

# Build for production
npm run build

# Run production build
npm start

# Type checking
npm run type-check

# Linting
npm run lint
```

## 🚀 Deployment

### Backend (Spring Boot)
```bash
# Build JAR
./gradlew bootJar

# Run JAR
java -jar build/libs/ai-truejobs-0.0.1-SNAPSHOT.jar
```

### Frontend (Next.js)
```bash
# Build for production
npm run build

# Start production server
npm start
```

### Docker Support
```dockerfile
# Backend Dockerfile
FROM openjdk:17-jdk-slim
COPY build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]

# Frontend Dockerfile
FROM node:18-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production
COPY . .
RUN npm run build
EXPOSE 3000
CMD ["npm", "start"]
```

## 🔐 Security

### Backend Security
- JWT token authentication
- Password hashing with BCrypt
- CORS configuration
- Input validation and sanitization
- Rate limiting for API endpoints

### Frontend Security
- Secure token storage (httpOnly cookies)
- XSS protection
- CSRF protection
- Secure API communication (HTTPS)
- Input validation on forms

## 📈 Performance

### Backend Optimizations
- Database query optimization
- Caching with Redis (optional)
- Async processing for AI operations
- Connection pooling
- API response compression

### Frontend Optimizations
- Next.js automatic code splitting
- Image optimization
- Lazy loading components
- Client-side caching
- Bundle size optimization

## 🤝 Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- OpenAI for AI capabilities
- Spring Boot community
- Next.js team
- Tailwind CSS
- All contributors and testers

## 📞 Support

For support, email support@aitruejobs.com or join our [Discord community](https://discord.gg/aitruejobs).
