import { describe, expect, it, vi } from 'vitest';
import { apiClient } from '../services/api';
import { fetchGeoData, fetchSpatialClusters, isPointFeature, type GeoJsonFeature } from '../services/gisApi';

describe('GIS API contract', () => {
  it('passes EPSG:4326 bbox parameters and AbortSignal to the GeoJSON endpoint', async () => {
    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: { type: 'FeatureCollection', features: [] },
    });
    const controller = new AbortController();

    await fetchGeoData('tbl_bridge', {
      minLon: 105,
      minLat: 20,
      maxLon: 107,
      maxLat: 22,
      limit: 500,
    }, { signal: controller.signal });

    expect(getSpy).toHaveBeenCalledWith('/api/datasets/tbl_bridge/geo', {
      params: expect.objectContaining({ minLon: 105, minLat: 20, maxLon: 107, maxLat: 22, limit: 500 }),
      signal: controller.signal,
      timeout: 45000,
    });
    getSpy.mockRestore();
  });

  it('uses the cluster endpoint for large datasets without changing the response shape', async () => {
    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: { type: 'FeatureCollection', features: [] },
    });
    await fetchSpatialClusters('tbl_road_sign', { zoom: 8 }, { signal: new AbortController().signal });
    expect(getSpy).toHaveBeenCalledWith('/api/datasets/tbl_road_sign/clusters', expect.any(Object));
    getSpy.mockRestore();
  });

  it('rejects null, non-point, invalid and out-of-range geometries before OpenLayers mapping', () => {
    const valid: GeoJsonFeature = {
      id: 'bridge-1', type: 'Feature', geometry: { type: 'Point', coordinates: [105.8, 21.0] }, properties: {},
    };
    expect(isPointFeature(valid)).toBe(true);
    expect(isPointFeature({ ...valid, geometry: { type: 'Point', coordinates: [null, 21] } })).toBe(false);
    expect(isPointFeature({ ...valid, geometry: { type: 'LineString', coordinates: [[105, 21]] } })).toBe(false);
    expect(isPointFeature({ ...valid, geometry: { type: 'Point', coordinates: [300, 21] } })).toBe(false);
  });
});
