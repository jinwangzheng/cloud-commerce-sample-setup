"use strict";(self.webpackChunkb2bspastore=self.webpackChunkb2bspastore||[]).push([[479],{5479:(M,l,n)=>{n.r(l),n.d(l,{SmartEditModule:()=>D});var r=n(4650),s=n(4947),S=n(2198),g=n(2986),C=n(5457);let h=(()=>{class t{constructor(e,o,i,c,d,v,f){if(this.cmsService=e,this.routingService=o,this.baseSiteService=i,this.zone=c,this.winRef=d,this.rendererFactory=v,this.config=f,this.isPreviewPage=!1,d.nativeWindow){const u=d.nativeWindow;u.smartedit=u.smartedit||{},u.smartedit.renderComponent=(p,m,F)=>this.renderComponent(p,m,F),u.smartedit.reprocessPage=this.reprocessPage}}processCmsPage(){this.baseSiteService.get().pipe((0,S.h)(e=>Boolean(e)),(0,g.q)(1)).subscribe(e=>{this.defaultPreviewCategoryCode=e.defaultPreviewCategoryCode,this.defaultPreviewProductCode=e.defaultPreviewProductCode,this.cmsService.getCurrentPage().pipe((0,S.h)(Boolean)).subscribe(o=>{this._currentPageId=o.pageId,this.goToPreviewPage(o),this.addPageContract(o)})})}addPageContract(e){const o=this.rendererFactory.createRenderer("body",null),i=this.winRef.document.body,c=[];Array.from(i.classList).forEach(d=>c.push(d)),c.forEach(d=>o.removeClass(i,d)),this.addSmartEditContract(i,o,e.properties)}goToPreviewPage(e){this.isPreviewPage||(this.isPreviewPage=!0,e.type===s.GV4.PRODUCT_PAGE&&this.defaultPreviewProductCode?this.routingService.go({cxRoute:"product",params:{code:this.defaultPreviewProductCode,name:""}}):e.type===s.GV4.CATEGORY_PAGE&&this.defaultPreviewCategoryCode&&this.routingService.go({cxRoute:"category",params:{code:this.defaultPreviewCategoryCode}}))}renderComponent(e,o,i){return e&&this.zone.run(()=>{i?o&&this.cmsService.refreshComponent(e):this._currentPageId?this.cmsService.refreshPageById(this._currentPageId):this.cmsService.refreshLatestPage()}),!0}reprocessPage(){}addSmartEditContract(e,o,i){i&&Object.keys(i).forEach(c=>{const d="data-"+c+"-",v=i[c];Object.keys(v).forEach(f=>{const u=v[f];"classes"===f?u.split(" ").forEach(m=>{o.addClass(e,m)}):o.setAttribute(e,d+f.split(/(?=[A-Z])/).join("-").toLowerCase(),u)})})}}return t.\u0275fac=function(e){return new(e||t)(r.LFG(s.ctt),r.LFG(s.Znh),r.LFG(s.Do$),r.LFG(r.R0b),r.LFG(s.X9E),r.LFG(r.FYo),r.LFG(C.Yl))},t.\u0275prov=r.Yz7({token:t,factory:t.\u0275fac,providedIn:"root"}),t})();const y=[{provide:s.ryK,useExisting:(()=>{class t extends s.ryK{constructor(e){super(),this.smartEditService=e}decorate(e,o,i){i&&this.smartEditService.addSmartEditContract(e,o,i.properties)}}return t.\u0275fac=function(e){return new(e||t)(r.LFG(h))},t.\u0275prov=r.Yz7({token:t,factory:t.\u0275fac,providedIn:"root"}),t})(),multi:!0},{provide:s.mR0,useExisting:(()=>{class t extends s.mR0{constructor(e){super(),this.smartEditService=e}decorate(e,o,i){i&&this.smartEditService.addSmartEditContract(e,o,i.properties)}}return t.\u0275fac=function(e){return new(e||t)(r.LFG(h))},t.\u0275prov=r.Yz7({token:t,factory:t.\u0275fac,providedIn:"root"}),t})(),multi:!0}];let w=(()=>{class t{constructor(e){this.smartEditService=e,this.smartEditService.processCmsPage()}}return t.\u0275fac=function(e){return new(e||t)(r.LFG(h))},t.\u0275mod=r.oAB({type:t}),t.\u0275inj=r.cJS({providers:[...y]}),t})(),D=(()=>{class t{}return t.\u0275fac=function(e){return new(e||t)},t.\u0275mod=r.oAB({type:t}),t.\u0275inj=r.cJS({imports:[w]}),t})()}}]);