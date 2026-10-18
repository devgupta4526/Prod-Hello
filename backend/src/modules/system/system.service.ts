import type { SystemRepository } from "./system.repo.js";
export class SystemService {
  constructor(private readonly repository: SystemRepository, private readonly minAppVersion = 1, private readonly maintenance = false) {}
  async health() {
    await this.repository.ready();
    return { status: "ok" as const };
  }
  config() {
    return {
      minAppVersion: this.minAppVersion,
      maintenance: this.maintenance,
      features: {
        purchasesEnabled: false,
        helloEnabled: false,
        websocketEnabled: false,
      },
      limits: {
        discoveryRadiusMaxKm: 25,
        helloDailyCap: 20,
        helloNewAccountDailyCap: 5,
      },
    };
  }
}
