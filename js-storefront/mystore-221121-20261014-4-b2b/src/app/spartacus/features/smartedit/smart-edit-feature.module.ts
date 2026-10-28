import { NgModule } from '@angular/core';
import { CmsConfig, provideConfig } from '@spartacus/core';
import {
  SMART_EDIT_FEATURE,
  SmartEditConfig,
  SmartEditRootModule,
} from '@spartacus/smartedit/root';

@NgModule({
  declarations: [],
  imports: [SmartEditRootModule],
  providers: [
    provideConfig(<CmsConfig>{
      featureModules: {
        [SMART_EDIT_FEATURE]: {
          module: () => import('@spartacus/smartedit').then((m) => m.SmartEditModule),
        },
      },
    }),
    provideConfig(<SmartEditConfig>{
      smartEdit: {
        storefrontPreviewRoute: 'cx-preview',
        allowOrigin: 'backoffice.cnx5-sub7for980-d1-public.model-t.cc.commerce.ondemand.com:443',
      },
    }),
  ],
})
export class SmartEditFeatureModule {}
