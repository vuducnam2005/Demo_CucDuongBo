import { describe, expect, it } from 'vitest';
import { applyCoordinatePatch } from '../components/assets/greenTreeForm';
import { JsonObject } from '../services/api';

describe('green-tree coordinate form patch', () => {
  it('sends an explicit null pair when an existing coordinate is cleared', () => {
    const attributes: JsonObject = { longitude: 108.25, latitude: 13.75, species_name: 'Sao den' };

    applyCoordinatePatch(attributes, {
      datasetKey: 'green_trees',
      mode: 'edit',
      hadCoordinates: true,
      longitude: undefined,
      latitude: 13.75,
    });

    expect(attributes).toEqual({
      longitude: null,
      latitude: null,
      species_name: 'Sao den',
    });
  });

  it('keeps a complete replacement coordinate pair', () => {
    const attributes: JsonObject = {};

    applyCoordinatePatch(attributes, {
      datasetKey: 'green_trees',
      mode: 'edit',
      hadCoordinates: true,
      longitude: 109.1,
      latitude: 14.2,
    });

    expect(attributes).toMatchObject({ longitude: 109.1, latitude: 14.2 });
  });
});
