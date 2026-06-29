/** Health payload returned by `GET /api/system/health` (mirrors the backend SystemHealthResponse). */
export interface SystemHealth {
  status: string;
  db: string;
}
