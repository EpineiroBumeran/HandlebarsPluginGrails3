package com.navent.handlebars

import grails.util.Holders

import java.text.SimpleDateFormat

import org.apache.commons.lang.LocaleUtils
import org.apache.commons.lang.StringUtils
import org.grails.web.util.WebUtils
import org.springframework.context.ApplicationContext

import asset.pipeline.grails.AssetsTagLib
import asset.pipeline.i18n.I18nTagLib

import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Helper
import com.github.jknack.handlebars.Options
import com.github.jknack.handlebars.cache.ConcurrentMapTemplateCache
import com.github.jknack.handlebars.io.FileTemplateLoader

@Singleton
class HandlebarsHelper {

	private Handlebars handlebars
	
	public Handlebars getHandlerBars() {
		if (this.handlebars == null)
			this.init()
		return this.handlebars
	}
	
	private File getTemplatesBaseDir() {
		def file
		def fullPath
		ApplicationContext appContext = Holders.getApplicationContext()
		if (Holders.getGrailsApplication().warDeployed) {
			fullPath = "${File.separator}assets"
			file = appContext.getResource(fullPath).getFile()
		} else {
			fullPath = "grails-app${File.separator}assets${File.separator}handlebars"
			file = new File(fullPath)
		}
		return file
	}
	
	private void init() {
		
		if(Holders.config.grails.plugins.handlebars.template.cache) {
			this.handlebars = new Handlebars(new FileTemplateLoader(getTemplatesBaseDir(),".html")).with(new ConcurrentMapTemplateCache())
		}
		else {
			this.handlebars = new Handlebars(new FileTemplateLoader(getTemplatesBaseDir(),".html"))
		}
		
		/*
		 * Registra el helper "i18nbundle" que permite invocar en handlebars el comportamiento del tag <asset:i18n/> del Asset pipeline plugin
		 * para crear la funcion $L('nombre.mensaje') que permite utilizar mensajes internacionalizados del lado de javascript
		 */
		this.handlebars.registerHelper("i18nbundle", new Helper<Object>() {
			
			public CharSequence apply(Object context, Options options) {
				
				def i18nBean = Holders.grailsApplication.mainContext.getBean(I18nTagLib.class.name);
				if(!i18nBean) {
					throw new Exception('I18n asset pipeline plugin is not installed in the application')
				}
				
				String locale = options.hash('locale')
				String messageFile = options.hash('name')
				
				return new Handlebars.SafeString(i18nBean.i18n(locale: locale, name: messageFile))
			}
		});
	
		/*
		 * Registra el helper "jsbundle" que permite invocar en handlebars el comportamiento del tag <asset:javascript src="bundle.js"/> del Asset pipeline plugin
		 */
		this.handlebars.registerHelper("jsbundle", new Helper<String>() {
			
			public CharSequence apply(String src, Options options) {
				
				def asset = Holders.grailsApplication.mainContext.getBean(AssetsTagLib.class.name);
				return new Handlebars.SafeString(asset.javascript(src: src))
			}
		});
	
	
		/*
		 * Registra el helper "cssbundle" que permite invocar en handlebars el comportamiento del tag <asset:stylesheet  href="bundle.css"/> del Asset pipeline plugin
		 */
		this.handlebars.registerHelper("cssbundle", new Helper<String>() {
			
			public CharSequence apply(String src, Options options) {
				
				def asset = Holders.grailsApplication.mainContext.getBean(AssetsTagLib.class.name);
				def media = options.hash('media')
				return new Handlebars.SafeString(asset.stylesheet (src: src, media: media ? media: 'screen'))
			}
		});
	
	
		/*
		 * Registra el helper "formato_fecha" para formatear una fecha {{formatear_fecha fecha formatoEntrada formatoSalida locale}}
		 * Ejemplo : {{formatear_fecha 25/12/2015 23:50 'dd-MM-yyyy HH:mm' 'dd-MMM-yyyy  HH:mm' locale}}
		 */
		this.handlebars.registerHelper("formato_fecha", new Helper<String>() {
			
			public CharSequence apply(String fecha, Options options) {
				
				String formatoEntrada = options.params[0]
				String formatoSalida = options.params[1]
				String locale = options.params[2]
				
				//obtengo un date del string fecha
				SimpleDateFormat format1 = new SimpleDateFormat(formatoEntrada);
				Date date = fecha != null ? format1.parse(fecha) : null
				
				//formateo el string
				SimpleDateFormat dateFormat = new SimpleDateFormat(formatoSalida, LocaleUtils.toLocale(locale));
				return date != null ? dateFormat.format(date) : null;
			}
		});

		/*
		 * Registra el helper "shouldBeSelected" usado dentro de un select html para determinar que opcion deberia estar seleccionada por defecto al cargar una pagina
		 * Ejemplo : {{shouldBeSelected "valorOpcion" valorObjetivo}}
		 */
		this.handlebars.registerHelper("shouldBeSelected", new Helper<String>() {
			
			public CharSequence apply(String value, Options options) {
				
				String optionSelected = options.params[0]
				
				if (optionSelected == value) {
					return ' selected';
				} else {
					return ''
				}
			}
		});
	
		/*
		 * Registra el helper "shouldBeChecked" usado dentro de un checkbox html para determinar si deberia estar checkeado por defecto al cargar una pagina
		 * Ejemplo : {{shouldBeChecked valorBooleano}}
		 */
		this.handlebars.registerHelper("shouldBeChecked", new Helper<Boolean>() {
			
			public CharSequence apply(Boolean checked, Options options) {
				
				if (checked) {
					return ' checked';
				} else {
					return ''
				}
			}
		});
	
		/*
		 * Registra el helper "shouldBeCheckedRadio" usado dentro de un radio group html para determinar cual deberia estar checkeado por defecto al cargar una pagina
		 * Ejemplo : {{shouldBeCheckedRadio "valorRadio" valorObjectivo}}
		 */
		this.handlebars.registerHelper("shouldBeCheckedRadio", new Helper<String>() {
			
			public CharSequence apply(String value, Options options) {
				
				String optionSelected = options.params[0]
				
				if (optionSelected == value) {
					return ' checked';
				} else {
					return ''
				}
			}
		});
	
		/*
		 * Sobreescribimos el i18n helper default de handlebars para poder customizar el manejo del locale que usa y el source desde donde obtiene los mensajes.
		 * En el contexto de la aplicacion que utilice este helper debe estar definido un bean denombre 'localeResolver' 
		 * que implemente la clase abstracta de spring AbstractLocaleContextResolver y la interfaz LocaleResolver de JAWR plugin,
		 * el cual permite obtener el locale a partir de un request.
		 */
		this.handlebars.registerHelper("i18n", new Helper<String>() {
		
			public CharSequence apply(final String key, final Options options) {
				
				if(StringUtils.isNotEmpty(key)) {
					def localeResolver = Holders.grailsApplication.mainContext.getBean("localeResolver")
					if(!localeResolver) {
						throw new Exception('Bean implementing LocaleResolver not found in application classpath')
					}
					
					Locale defaultLocale = localeResolver.resolveLocale(WebUtils.retrieveGrailsWebRequest().getRequest())
				    Locale locale = LocaleUtils.toLocale((String) options.hash("locale", defaultLocale.toString()))
					def localSource = Holders.grailsApplication.mainContext.getBean("i18nMessageSource")
				   
				    return localSource.message(key, locale, options.params)
				}
				else {
					return ''
				}
			}
		});

 }	
}
