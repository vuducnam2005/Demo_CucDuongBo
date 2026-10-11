import { JsonObject } from '../../services/api';

interface CoordinatePatch {
  datasetKey: string;
  mode: 'create' | 'edit';
  hadCoordinates: boolean;
  longitude?: number;
  latitude?: number;
}

export const applyCoordinatePatch = (
  attributes: JsonObject,
  { datasetKey, mode, hadCoordinates, longitude, latitude }: CoordinatePatch
) => {
  const clearedCoordinates = datasetKey === 'green_trees'
    && mode === 'edit'
    && hadCoordinates
    && (longitude === undefined || latitude === undefined);

  if (clearedCoordinates) {
    attributes.longitude = null;
    attributes.latitude = null;
    return;
  }

  if (longitude === undefined) delete attributes.longitude;
  else attributes.longitude = longitude;
  if (latitude === undefined) delete attributes.latitude;
  else attributes.latitude = latitude;
};
