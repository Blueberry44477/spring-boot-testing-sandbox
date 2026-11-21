import { APIRequestContext, expect } from '@playwright/test';
import crypto from 'node:crypto';

export function uniqueTag(prefix: string): string {
  return `${prefix}-${crypto.randomUUID().slice(0, 8)}`;
}

export async function seedStudent(
  request: APIRequestContext,
  data: { age: number; entryScore: number; regular?: boolean; code?: string }
): Promise<{ id: number; code: string }> {
  const code = data.code ?? uniqueTag('STU');
  const response = await request.post('/students', {
    data: {
      code,
      age: data.age,
      entryScore: data.entryScore,
      regular: data.regular ?? false,
    },
  });
  expect(response.ok()).toBeTruthy();
  return response.json();
}

export async function seedCourse(
  request: APIRequestContext,
  data: { priceKopecks?: number; capacity?: number; level: 1 | 2 | 3; code?: string }
): Promise<{ id: number; code: string }> {
  const code = data.code ?? uniqueTag('CRS');
  const response = await request.post('/courses', {
    data: {
      code,
      price: data.priceKopecks ?? 100_000,
      capacity: data.capacity ?? 10,
      level: data.level,
    },
  });
  expect(response.ok()).toBeTruthy();
  return response.json();
}
