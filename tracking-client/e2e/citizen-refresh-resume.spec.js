const { test, expect } = require('@playwright/test');

test.describe('Citizen request tracking', () => {
  test('should keep tracked request after refresh and keep route details visible', async ({ page }) => {
    const emergencyId = 'EMG-E2E-REFRESH-1';
    const assignedAmbulanceId = 'AMB-101';

    await page.route(`**/api/emergencies/public/${emergencyId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          emergencyId,
          latitude: 18.5241,
          longitude: 73.8527,
          priority: 'HIGH',
          status: 'ON_ROUTE',
          assignedAmbulanceId,
        }),
      });
    });

    await page.route(`**/api/tracking/public/ambulances/${assignedAmbulanceId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          ambulanceId: assignedAmbulanceId,
          latitude: 18.5215,
          longitude: 73.8498,
          speed: 32.4,
          heading: 34,
          status: 'ON_ROUTE',
        }),
      });
    });

    await page.route('**/route/v1/driving/**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 'Ok',
          routes: [
            {
              distance: 1800,
              duration: 110,
              geometry: {
                coordinates: [
                  [73.8498, 18.5215],
                  [73.8512, 18.5229],
                  [73.8527, 18.5241],
                ],
                type: 'LineString',
              },
            },
          ],
        }),
      });
    });

    await page.goto('/citizen');

    await page.getByLabel('Track Existing Request ID').fill(emergencyId);
    await page.getByRole('button', { name: 'Track' }).click();

    await expect(page.getByText(new RegExp(`Request ID:\\s*${emergencyId}`))).toBeVisible();
    await expect(page.getByText(/Status:\s*ON_ROUTE/)).toBeVisible();
    await expect(page.getByText('Live path shown on map')).toBeVisible();
    await expect(page.getByText(/Estimated Arrival:\s*2 min/)).toBeVisible();

    await page.reload();

    await expect(page.getByText(new RegExp(`Request ID:\\s*${emergencyId}`))).toBeVisible();
    await expect(page.getByText(/Status:\s*ON_ROUTE/)).toBeVisible();
    await expect(page.getByText('Live path shown on map')).toBeVisible();
    await expect(page.getByText(/Estimated Arrival:\s*2 min/)).toBeVisible();
  });
});
