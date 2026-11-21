import { Locator, Page, expect } from '@playwright/test';

export interface StudentInput {
  code: string;
  age: number;
  score: number;
  regular?: boolean;
}

export interface CourseInput {
  code: string;
  priceUah: number;
  capacity: number;
  level: 1 | 2 | 3;
}

export interface EnrollmentInput {
  reference: string;
  studentId?: number;
  courseId?: number;
  coupon?: boolean;
}

export class EnrollmentPage {
  readonly page: Page;

  // Global feedback
  readonly notice: Locator;
  readonly errorAlert: Locator;

  // Student section
  readonly studentForm: Locator;
  readonly studentResult: Locator;
  readonly studentInfo: Locator;

  // Course section
  readonly courseForm: Locator;
  readonly courseResult: Locator;
  readonly courseInfo: Locator;

  // Enrollment creation & result
  readonly enrollmentForm: Locator;
  readonly enrollmentResult: Locator;
  readonly enrollmentReference: Locator;
  readonly enrollmentStatus: Locator;
  readonly enrollmentId: Locator;
  readonly enrollmentTotal: Locator;
  readonly enrollmentPrepaid: Locator;
  readonly enrollmentRefund: Locator;

  // Enrollment actions
  readonly completeButton: Locator;
  readonly cancelForm: Locator;
  readonly lookupForm: Locator;

  constructor(page: Page) {
    this.page = page;

    this.notice = page.locator('#notice');
    this.errorAlert = page.locator('#error');

    this.studentForm = page.locator('#student-form');
    this.studentResult = page.getByTestId('student-result');
    this.studentInfo = page.locator('#student-info');

    this.courseForm = page.locator('#course-form');
    this.courseResult = page.getByTestId('course-result');
    this.courseInfo = page.locator('#course-info');

    this.enrollmentForm = page.getByTestId('enrollment-form');
    this.enrollmentResult = page.getByTestId('enrollment-result');
    this.enrollmentReference = page.locator('#enrollment-reference');
    this.enrollmentStatus = page.getByTestId('enrollment-status');
    this.enrollmentId = page.getByTestId('enrollment-id');
    this.enrollmentTotal = page.getByTestId('enrollment-total');
    this.enrollmentPrepaid = page.getByTestId('enrollment-prepaid');
    this.enrollmentRefund = page.getByTestId('enrollment-refund');

    this.completeButton = page.locator('#complete');
    this.cancelForm = page.locator('#cancel-form');
    this.lookupForm = page.locator('#lookup-form');
  }

  async goto() {
    await this.page.goto('/');
  }

  async createStudent(input: StudentInput) {
    await this.studentForm.locator('input[name="code"]').fill(input.code);
    await this.studentForm.locator('input[name="age"]').fill(String(input.age));
    await this.studentForm.locator('input[name="score"]').fill(String(input.score));
    const regularCheckbox = this.studentForm.locator('input[name="regular"]');
    if (input.regular !== undefined) {
      await regularCheckbox.setChecked(input.regular);
    }
    await this.studentForm.getByRole('button', { name: 'Створити слухача' }).click();
    await expect(this.studentResult).toBeVisible();
  }

  async createCourse(input: CourseInput) {
    await this.courseForm.locator('input[name="code"]').fill(input.code);
    await this.courseForm.locator('input[name="price"]').fill(String(input.priceUah));
    await this.courseForm.locator('input[name="capacity"]').fill(String(input.capacity));
    await this.courseForm.locator('input[name="level"]').fill(String(input.level));
    await this.courseForm.getByRole('button', { name: 'Створити курс' }).click();
    await expect(this.courseResult).toBeVisible();
  }

  async fillAndSubmitEnrollment(input: EnrollmentInput) {
    await this.enrollmentForm.locator('input[name="reference"]').fill(input.reference);
    if (input.studentId !== undefined) {
      await this.enrollmentForm.locator('input[name="studentId"]').fill(String(input.studentId));
    }
    if (input.courseId !== undefined) {
      await this.enrollmentForm.locator('input[name="courseId"]').fill(String(input.courseId));
    }
    if (input.coupon !== undefined) {
      await this.enrollmentForm.locator('input[name="coupon"]').setChecked(input.coupon);
    }
    await this.enrollmentForm.getByRole('button', { name: 'Зарахувати на курс' }).click();
  }

  async enroll(input: EnrollmentInput) {
    await this.fillAndSubmitEnrollment(input);
    await expect(this.enrollmentResult).toBeVisible();
  }

  async completeEnrollment() {
    await this.completeButton.click();
  }

  async cancelEnrollment(daysBeforeStart: number) {
    await this.cancelForm.locator('input[name="days"]').fill(String(daysBeforeStart));
    await this.cancelForm.getByRole('button', { name: 'Скасувати зарахування' }).click();
  }

  async lookupEnrollment(id: string | number) {
    await this.lookupForm.locator('input[name="id"]').fill(String(id));
    await this.lookupForm.getByRole('button', { name: 'Знайти заявку' }).click();
    await expect(this.enrollmentResult).toBeVisible();
  }
}
