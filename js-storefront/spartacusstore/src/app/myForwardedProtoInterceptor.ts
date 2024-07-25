import { Injectable, inject, Inject, PLATFORM_ID } from '@angular/core';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Observable } from 'rxjs';
import { isPlatformServer } from '@angular/common';
import { LoggerService } from '@spartacus/core';

@Injectable({
  providedIn: 'root'
})

export class myForwardedProtoInterceptor implements HttpInterceptor {

  constructor(@Inject(PLATFORM_ID) private platformId: Object, private logger: LoggerService) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
   
    this.logger.log('Making request to: '+req.url);

    var fianlReq=req;

    // Check if running on the server and the request is to the internal OCC API
    if (isPlatformServer(this.platformId) && req.url.startsWith('http://api.default.svc.cluster.local:8081')) {
      // Clone the request and add the header
      fianlReq = req.clone({
        headers: req.headers.set('X-Forwarded-Proto', 'https')
      });
    }
   
    return next.handle(fianlReq);
  }
}  
