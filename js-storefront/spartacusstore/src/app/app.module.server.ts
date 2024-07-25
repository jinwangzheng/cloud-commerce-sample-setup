import { NgModule } from '@angular/core';
import { ServerModule } from '@angular/platform-server';

import { AppModule } from './app.module';
import { AppComponent } from './app.component';
import { provideServer } from '@spartacus/setup/ssr';

import {myForwardedProtoInterceptor} from "./myForwardedProtoInterceptor";
import { HTTP_INTERCEPTORS } from '@angular/common/http';

@NgModule({
  imports: [
    AppModule,
    ServerModule,
  ],
  bootstrap: [AppComponent],
  providers: [
    ...provideServer({
       serverRequestOrigin: process.env['SERVER_REQUEST_ORIGIN'],
     }),
    { provide: HTTP_INTERCEPTORS, multi:true, useExisting: myForwardedProtoInterceptor }
  ],
})
export class AppServerModule {}
