import { test, expect } from '@playwright/test';
import { EnrollmentPage } from '../pages/EnrollmentPage';
import { seedCourse, seedStudent, uniqueTag } from '../helpers/api';

test.describe('Course Enrollment E2E', () => {
  test('Positive: create student, course, enroll via UI', async ({ page }) => {
    const ui = new EnrollmentPage(page);
    const studentCode = uniqueTag('STU');
    const courseCode = uniqueTag('CRS');
    const reference = uniqueTag('REF');

    await ui.goto();

    await ui.createStudent({ code: studentCode, age: 20, score: 85 });
    await expect(ui.studentInfo).toContainText(studentCode);

    await ui.createCourse({ code: courseCode, priceUah: 1000, capacity: 10, level: 1 });
    await expect(ui.courseInfo).toContainText(courseCode);

    await ui.enroll({ reference });
    await expect(ui.enrollmentReference).toHaveText(reference);
    await expect(ui.enrollmentStatus).toHaveText('Зараховано');
    await expect(ui.enrollmentStatus).toHaveAttribute('data-status', 'ENROLLED');
    await expect(ui.enrollmentTotal).toHaveAttribute('data-kopecks', '100000');

    // Verify persistence in DB after full page reload
    await page.reload();
    await expect(ui.enrollmentResult).toBeVisible();
    await expect(ui.enrollmentReference).toHaveText(reference);
    await expect(ui.enrollmentStatus).toHaveText('Зараховано');
    await expect(ui.enrollmentStatus).toHaveAttribute('data-status', 'ENROLLED');
  });

  test('Complete enrollment', async ({
    page,
    request,
  }) => {
    const student = await seedStudent(request, { age: 22, entryScore: 75 });
    const course = await seedCourse(request, { priceKopecks: 150_000, capacity: 5, level: 2 });
    const reference = uniqueTag('REF');

    const ui = new EnrollmentPage(page);
    await ui.goto();

    await ui.enroll({ reference, studentId: student.id, courseId: course.id });
    await expect(ui.enrollmentStatus).toHaveAttribute('data-status', 'ENROLLED');

    await ui.completeEnrollment();
    await expect(ui.enrollmentStatus).toHaveText('Завершено');
    await expect(ui.enrollmentStatus).toHaveAttribute('data-status', 'COMPLETED');

    await page.reload();
    await expect(ui.enrollmentResult).toBeVisible();
    await expect(ui.enrollmentStatus).toHaveText('Завершено');
    await expect(ui.enrollmentStatus).toHaveAttribute('data-status', 'COMPLETED');
  });

  test('Cancel enrollment with full refund', async ({
    page,
    request,
  }) => {
    const student = await seedStudent(request, { age: 21, entryScore: 50 });
    const course = await seedCourse(request, { priceKopecks: 200_000, capacity: 5, level: 1 });
    const reference = uniqueTag('REF');

    const ui = new EnrollmentPage(page);
    await ui.goto();

    await ui.enroll({ reference, studentId: student.id, courseId: course.id });
    await ui.cancelEnrollment(7);

    await expect(ui.enrollmentStatus).toHaveText('Скасовано');
    await expect(ui.enrollmentStatus).toHaveAttribute('data-status', 'CANCELLED');
    await expect(ui.enrollmentRefund).toHaveAttribute('data-kopecks', '200000');

    await page.reload();
    await expect(ui.enrollmentResult).toBeVisible();
    await expect(ui.enrollmentStatus).toHaveText('Скасовано');
    await expect(ui.enrollmentStatus).toHaveAttribute('data-status', 'CANCELLED');
    await expect(ui.enrollmentRefund).toHaveAttribute('data-kopecks', '200000');
  });

  test('Backend rejection: shows error on repeated completion of enrollment', async ({
    page,
    request,
  }) => {
    const student = await seedStudent(request, { age: 25, entryScore: 90 });
    const course = await seedCourse(request, { priceKopecks: 100_000, capacity: 5, level: 1 });
    const reference = uniqueTag('REF');

    const ui = new EnrollmentPage(page);
    await ui.goto();

    await ui.enroll({ reference, studentId: student.id, courseId: course.id });
    await ui.completeEnrollment();
    await expect(ui.enrollmentStatus).toHaveAttribute('data-status', 'COMPLETED');

    // Second complete attempt must be rejected by backend
    await ui.completeEnrollment();
    await expect(ui.errorAlert).toBeVisible();
    await expect(ui.errorAlert).toContainText('Cannot complete an enrollment that is not ENROLLED');
    await expect(ui.enrollmentStatus).toHaveAttribute('data-status', 'COMPLETED');
  });

  test('Business rule (level 3 requires age >= 18 and score >= 80): allows eligible and rejects underage', async ({
    page,
    request,
  }) => {
    const course = await seedCourse(request, { priceKopecks: 120_000, capacity: 10, level: 3 });
    const eligibleStudent = await seedStudent(request, { age: 18, entryScore: 80, regular: false });
    const underageStudent = await seedStudent(request, { age: 17, entryScore: 95, regular: true });

    const ui = new EnrollmentPage(page);
    await ui.goto();

    // Case A: rule condition met (age = 18, score = 80) -> enrollment succeeds
    await ui.enroll({
      reference: uniqueTag('REF-OK'),
      studentId: eligibleStudent.id,
      courseId: course.id,
    });
    await expect(ui.enrollmentStatus).toHaveText('Зараховано');
    await expect(ui.enrollmentStatus).toHaveAttribute('data-status', 'ENROLLED');

    // Case B: rule condition NOT met (age = 17, even with score = 95 and regular = true) -> backend rejects
    await ui.fillAndSubmitEnrollment({
      reference: uniqueTag('REF-FAIL'),
      studentId: underageStudent.id,
      courseId: course.id,
    });
    await expect(ui.errorAlert).toBeVisible();
    await expect(ui.errorAlert).toContainText('Student not eligible for this course level');
  });
});
