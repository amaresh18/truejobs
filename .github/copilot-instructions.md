# AI TrueJobs - Copilot Instructions

## Purpose
These instructions guide GitHub Copilot to understand the **full context** of the AI-powered recruitment platform and make accurate, non-breaking changes to the codebase.

---

## Project Overview
This is an **enterprise-grade, AI-powered job search and recruitment platform** with:

**Backend (Spring Boot + Java)**
- Framework: Spring Boot 3.2.0, Java 17
- Database: PostgreSQL (prod)
- Security: Spring Security + JWT
- Build Tool: Gradle
- AI: OpenAI API for embeddings, ATS scoring, and job content generation

**Frontend (Next.js + TypeScript)**
- Framework: Next.js 14
- Styling: Tailwind CSS
- State: Zustand
- Forms: React Hook Form + Yup
- Animations: Framer Motion
- Icons: Lucide React
- API Calls: Axios

---

## Key Features
- **AI ATS Scoring**: Match resumes to jobs via cosine similarity
- **Smart Matching**: Embedding-based recommendations
- **Resume Analysis**: AI feedback & improvement tips
- **Application Tracking**: Status changes + AI feedback

---

## Known Issues (Fix These First)
1. **Buttons not working**: Sign In, Create Account, Apply Now must function correctly for all roles.
2. **Header/Footer duplication**: Remove duplicate UI elements, ensure consistent layout across pages.
3. **Job filtering issues**: All filters (location, skills, salary, etc.) must return relevant results only.
4. **Draft jobs visible**: Only show published jobs in search results.
5. **Publish/Unpublish toggle**: Implement proper state change.
6. **Application rejection**: Require a mandatory comment for rejection.
7. **Schema mismatches**: Ensure consistent variable names/types across DB, backend, frontend.
8. **Repeated API calls**: Eliminate unnecessary calls (e.g., repeated `me` endpoint hits).

---

## UI/UX Upgrade Requirements
- Use **modern sleek components** from:
  - [https://21st.dev/](https://21st.dev/)
  - [https://flexiple.com/](https://flexiple.com/)
- Fully responsive design.
- Clear loading states and error boundaries.
- Smooth transitions (Framer Motion) for a professional feel.

---

## Coding Guidelines for Copilot
### Backend (Java)
- Follow Spring Boot conventions.
- Use Lombok to reduce boilerplate.
- Use `@RestController` for APIs, `@Service` for business logic, `@Repository` for persistence.
- Always validate inputs.
- Implement proper exception handling.
- Use `@Transactional` where needed.

### Frontend (TypeScript + React)
- Use functional components with hooks.
- Strong TypeScript typing everywhere.
- Use custom hooks for reusable logic.
- Follow Next.js app router best practices.
- Organize components logically.

---

## Entity Relationships
- **User** → many **Resumes**, **Applications**, **Jobs** (if recruiter)
- **Job** → many **Applications**, belongs to **User**
- **Resume** → belongs to **User**, many **Applications**
- **Application** → belongs to **User**, **Job**, **Resume**

---

## API Conventions
- RESTful: `/api/{resource}`
- Auth: Bearer token in headers
- Pagination: `page`, `size`
- Search: `search` param
- Filters: use explicit filter params

---

## AI Integration
- Embeddings: `text-embedding-ada-002`
- Chat: `gpt-3.5-turbo` for job/resume content
- ATS Score: cosine similarity between embeddings
- External Profile Search: fetch LinkedIn & external profiles by job description; rank by ATS score

---

## Copilot Directives
When making changes:
1. **Do not break existing features.**
2. Resolve compile-time errors in Java, TypeScript, or Gradle.
3. Remove unused imports, files, and redundant logic.
4. Improve performance & readability.
5. After each change, ensure:
   - All tests pass (if available)
   - App builds without errors
   - UI matches the modern design spec
6. Commit with clear messages explaining the change.

---

## End Goal
A **bug-free, visually modern, AI-powered recruitment platform** with clean, maintainable code, zero compile errors, and all features working as expected.