import { PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Meta } from '@angular/platform-browser';
import { OccConfig, provideConfigFactory } from '@spartacus/core';
import { LoggerService } from '@spartacus/core';

const OCC_BASE_URL_META_TAG_NAME = 'occ-backend-base-url';
const OCC_BASE_URL_META_TAG_PLACEHOLDER = 'OCC_BACKEND_BASE_URL_VALUE';

export function myOccConfigFactory(): OccConfig {
  const meta = inject(Meta);
  const logger = inject(LoggerService);
  const platformId = inject(PLATFORM_ID);
  const isBrowser = isPlatformBrowser(platformId);
 
  const baseUrlFromMeta = getMetaTagContent(OCC_BASE_URL_META_TAG_NAME, meta);
 
  var csrBaseUrl =baseUrlFromMeta && baseUrlFromMeta !== OCC_BASE_URL_META_TAG_PLACEHOLDER ? baseUrlFromMeta: '';
 
  var ssrBaseUrl='http://api.default.svc.cluster.local:8081';

  var finalBaseUrl = isBrowser ? csrBaseUrl : ssrBaseUrl;

  logger.log('baseUrlFromMeta: '+baseUrlFromMeta);
  logger.log('isBrowser: '+isBrowser);
  logger.log('csrBaseUrl: '+csrBaseUrl);
  logger.log('ssrBaseUrl: '+ssrBaseUrl);
  logger.log('finalBaseUrl: '+finalBaseUrl);
 
  return {
    backend: {
      occ: { baseUrl: finalBaseUrl },
      media: {baseUrl: csrBaseUrl }
    }
  }
}

function getMetaTagContent(name: string, meta: Meta) {
  const metaTag = meta.getTag(`name="${name}"`);
  return metaTag && metaTag.content;
}

