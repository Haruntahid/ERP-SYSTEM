# Product Requirements (living document)

## Vision
A configurable School/University ERP sold as SaaS to many institutions. Each institution (tenant) gets an isolated database, its own branding and configuration, and only the modules its plan includes.

## Users
Platform operator, school admin, registrar, accountant, exam controller, department head, teacher, student, guardian.

## Release plan
| Phase | Scope |
|---|---|
| 1 | Auth, RBAC with scope, institution and academic structure, students, staff, attendance, fees and payments, notices |
| 2 | Exams, results, GPA/CGPA, transcripts, timetable, guardian portal, SMS gateway |
| 3 | LMS, library, hostel, transport, HR and payroll |
| 4 | Analytics, mobile app, tenant billing and usage metering, white label domains |

## Non-functional requirements
- Strict isolation between schools (database per school).
- Bangla and English UI.
- Audit trail for marks, fees, results and permission changes.
- Published results and posted payments are immutable (corrections via a formal flow).
- Daily backups with tested restores per tenant database.
- Target: hundreds of schools on shared infrastructure, with the ability to move a school to a dedicated server.

## Open questions (fill before planning the related feature)
- Currencies and payment gateways to support at launch.
- Grading scales to support at launch.
- SMS provider and sender id handling per school.
- Pricing model (per student or flat per plan).
