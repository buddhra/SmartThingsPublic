/**
 *  Window Forecast
 *	Monitors inside and outside temperatures and recommends if windows should be open or closed. Uses forecasted high temperature to decide.
 *
 *  Copyright 2016 Matthew Budraitis
 *
 * 	Based in part on the "Weather Windows" SmartApp by Eric Gideon.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
	name: "Window Forecast",
	namespace: "buddhra",
	author: "Matthew Budraitis",
        category: "Green Living",
	description: "Monitors inside and outside temperatures and recommends if windows should be open or closed. Uses forecasted high temperature to decide.",
	iconUrl: "https://s3.amazonaws.com/smartthings-device-icons/Home/home9-icn.png",
	iconX2Url: "https://s3.amazonaws.com/smartthings-device-icons/Home/home9-icn@2x.png"
)


preferences {
	section( "Select your temperature sensors"){
    	input "insideTemp", "capability.temperatureMeasurement", required: true, title: "Inside temperature"
        input "outsideTemp", "capability.temperatureMeasurement", required: true, title: "Outside temperature"
    }
	section( "Set the temperature range for your comfort zone..." ) {
		input "minTemp", "number", title: "Minimum inside temperature", required: true
		input "maxTemp", "number", title: "Maximum inside temperature", required: true
	}
	section( "Select (optional) windows to check..." ) {
		input "contacts", "capability.contactSensor", multiple: true, required: false
	}
	section( "Set your location" ) {
		input "zipCode", "text", title: "Zip code", required: true
	}
	section( "Notifications" ) {
		input "sendPush", "enum", title: "Send a push notification?", metadata:[values:["Yes","No"]], required:true
	}
}


def installed() {
	initialize()
}

def updated() {
	unsubscribe()
	initialize()
}

def initialize(){
    subscribe(insideTemp, "temperature", inTempHandler)
    subscribe(outsideTemp, "temperature", outTempHandler)
        
    state.lastRecommendation = "don't know" //remembers last state in persistent memory
    
    def outTempDef = outsideTemp.latestValue("temperature")
    if(!outTempDef){
    	outTempDef = 70
    }
    double outTempD = outTempDef
    int outTempI = outTempD.round(0)
    state.lastOutTemp = outTempI
    
    def inTempDef = insideTemp.latestValue("temperature")
    if(!inTempDef){
    	inTempDef = 70
    }
    double inTempD = inTempDef
    int inTempI = inTempD.round(0)
    state.lastInTemp = inTempI
    
    state.trend = "not sure yet"
    state.inside = "not sure yet"
    state.day = "not sure yet"
    
    def highTempStr = weatherCheck()
   	state.highTemp = highTempStr.toInteger()
    
    //INITIALIZE DAY
    if(state.highTemp > maxTemp){
        state.day = "warm"
    }else if(state.highTemp < minTemp){
        state.day = "cool"
    }else{
        state.day = "comfortable"
    }
    
    //INTIIALIZE INSIDE
    if(state.lastInTemp > maxTemp){
        state.inside = "high"
    }else if(state.lastInTemp < minTemp){
        state.inside = "low"
    }else{
        state.inside = "comfortable"
    }
    
    log.trace "Window Forecast initialized. The temperature is $state.inside inside at $state.lastInTemp°F. It is $state.lastOutTemp°F outside and the trend is $state.trend. The day is $state.day with a forecasted high of $state.highTemp°F."
}

def inTempHandler(evt){
	log.trace "Window Forecast saw that ${evt.displayName} changed to ${evt.value}°F. lastInTemp is $state.lastInTemp°F"
    double inTemp = evt.value.toDouble()

	//INSIDE TEMP FILTERING
    if(state.lastInTemp-inTemp > 1){
    	inTemp = state.lastInTemp-1
        log.trace "Inside temp is filtered to $inTemp°F because it decreased by more than the maximum amount"
    }
    if(inTemp-state.lastInTemp > 1){
    	inTemp = state.lastInTemp+1
        log.trace "Inside temp is filtered to $inTemp°F because it increased by more than the maximum amount"
    }
    if((state.lastInTemp-inTemp).abs() < 1){
    	inTemp = state.lastInTemp
        log.trace "Inside temp is filtered to $inTemp°F because it hasn't changed by the minimum amount."
    }
    if((state.lastInTemp-inTemp).abs() == 1){
    	state.lastInTemp = inTemp.toInteger()
        log.trace ("Inside temp of $inTemp°F was saved to lastInTemp")   
    	
        if(inTemp > maxTemp){
            state.inside = "high"
        }else if(inTemp < minTemp){
            state.inside = "low"
        }else{
            state.inside = "comfortable"
        }
        logic() //run logic function since there was an update to the inside temp
    }
}

def outTempHandler(evt){
	log.trace "Window Forecast saw that ${evt.displayName} changed to ${evt.value}°F. lastOutTemp is $state.lastOutTemp°F"
	double outTempD = evt.value.toDouble()
    int outTempI = outTempD.round(0)
    def outTemp = outTempI

	//DETERMINE TREND    
    if((state.lastOutTemp-outTemp).abs() >= 1){
        if(state.lastOutTemp > outTemp){
            state.trend = "decreasing"
        }
        if(state.lastOutTemp < outTemp){
            state.trend = "increasing"
        }
    	state.lastOutTemp = outTemp.toInteger()
    }
    
	def highTempStr = weatherCheck()
   	state.highTemp = highTempStr.toInteger()
    //UPDATE DAY
    if(state.highTemp > maxTemp){
        state.day = "warm"
    }else if(state.highTemp < minTemp){
        state.day = "cool"
    }else{
        state.day = "comfortable"
    }
    
	logic() //run logic function since outside temperature changed
}

def logic(){
    def currentLogic = 0
    def windowsRecommendation = "not sure yet"
	def openWindows = contacts.findAll { it?.latestValue("contact") == 'open' }

	//DETERMINE LOGIC
	if(state.lastInTemp == state.lastOutTemp){
    	currentLogic = 2 //2,5,8,11,14,17,20,23,26,29,32,35,38,41,44,47,50,53
        windowsRecommendation = "don't care"
    }else if(state.inside == "high"){
    	if(state.lastInTemp > state.lastOutTemp){
        	currentLogic = 1 //1,10,19,28,37,46
       		windowsRecommendation = "open"
        }
        if(state.lastInTemp < state.lastOutTemp){
        	currentLogic = 3 //3,12,21,30,39,48
            windowsRecommendation = "close"
        }
    }else if(state.day == "warm"){
    	if(state.trend == "increasing"){
        	if(state.inside == "comfortable"){
            	if(state.lastInTemp > state.lastOutTemp){
                	currentLogic = 4 
       				windowsRecommendation = "open"
                }
                if(state.lastInTemp < state.lastOutTemp){
                	currentLogic = 6 
		       		windowsRecommendation = "close"
                }
            }else if(state.inside == "low"){
            	if(state.lastInTemp > state.lastOutTemp){
                	currentLogic = 7 
       				windowsRecommendation = "open"
                }
                if(state.lastInTemp < state.lastOutTemp){
                	currentLogic = 9 
		       		windowsRecommendation = "close"
                }
            }
        }else if(state.trend == "decreasing"){
        	if(state.inside == "comfortable"){
            	if(state.lastInTemp > state.lastOutTemp){
                	currentLogic = 13 
       				windowsRecommendation = "don't care"
                }
                if(state.lastInTemp < state.lastOutTemp){
                	currentLogic = 15 
		       		windowsRecommendation = "close"
                }
            }else if(state.inside == "low"){
            	if(state.lastInTemp > state.lastOutTemp){
                	currentLogic = 16 
       				windowsRecommendation = "don't care"
                }
                if(state.lastInTemp < state.lastOutTemp){
                	currentLogic = 18 
		       		windowsRecommendation = "don't care"
                }
            }
        }
    }else if(state.day == "comfortable"){
    	if(state.trend == "increasing"){
        	if(state.inside == "comfortable"){
            	if(state.lastInTemp > state.lastOutTemp){
                	currentLogic = 22 
       				windowsRecommendation = "don't care"
                }
                if(state.lastInTemp < state.lastOutTemp){
                	currentLogic = 24 
		       		windowsRecommendation = "close"
                }
            }else if(state.inside == "low"){
            	if(state.lastInTemp > state.lastOutTemp){
                	currentLogic = 25 
       				windowsRecommendation = "don't care"
                }
                if(state.lastInTemp < state.lastOutTemp){
                	currentLogic = 27 
		       		windowsRecommendation = "don't care"
                }
            }
        }else if(state.trend == "decreasing"){
        	if(state.inside == "comfortable"){
            	if(state.lastInTemp > state.lastOutTemp){
                	currentLogic = 31 
       				windowsRecommendation = "don't care"
                }
                if(state.lastInTemp < state.lastOutTemp){
                	currentLogic = 33 
		       		windowsRecommendation = "don't care"
                }
            }else if(state.inside == "low"){
            	if(state.lastInTemp > state.lastOutTemp){
                	currentLogic = 34 
       				windowsRecommendation = "close"
                }
                if(state.lastInTemp < state.lastOutTemp){
                	currentLogic = 36 
		       		windowsRecommendation = "open"
                }
            }
        }
    }else if(state.day == "cool"){
    	if(state.trend == "increasing"){
        	if(state.inside == "comfortable"){
            	if(state.lastInTemp > state.lastOutTemp){
                	currentLogic = 40 
       				windowsRecommendation = "close"
                }
                if(state.lastInTemp < state.lastOutTemp){
                	currentLogic = 42 
		       		windowsRecommendation = "open"
                }
            }else if(state.inside == "low"){
            	if(state.lastInTemp > state.lastOutTemp){
                	currentLogic = 43 
       				windowsRecommendation = "close"
                }
                if(state.lastInTemp < state.lastOutTemp){
                	currentLogic = 45 
		       		windowsRecommendation = "open"
                }
            }
        }else if(state.trend == "decreasing"){
        	if(state.inside == "comfortable"){
            	if(state.lastInTemp > state.lastOutTemp){
                	currentLogic = 49 
       				windowsRecommendation = "don't care"
                }
                if(state.lastInTemp < state.lastOutTemp){
                	currentLogic = 51 
		       		windowsRecommendation = "don't care"
                }
            }else if(state.inside == "low"){
            	if(state.lastInTemp > state.lastOutTemp){
                	currentLogic = 52 
       				windowsRecommendation = "close"
                }
                if(state.lastInTemp < state.lastOutTemp){
                	currentLogic = 54 
		       		windowsRecommendation = "open"
                }
            }
        }
    }
	 
	//CHECK FOR STATE CHANGE
    //CLOSE WINDOWS
    if(windowsRecommendation == "close" && state.lastRecommendation == "open"){
        state.lastRecommendation = windowsRecommendation
        if(contacts && openWindows){
            sendMessage("It is $state.lastInTemp°F inside and $state.lastOutTemp°F outside. Close ${openWindows.join(', ')}.")
        }else if(contacts){ 
            log.trace ("It is $state.lastInTemp°F inside and $state.lastOutTemp°F outside, but windows are already closed.")
        }else sendMessage("It is $state.lastInTemp°F inside and $state.lastOutTemp°F outside. Close windows and skylights.")
    }
    //OPEN WINDOWS
    if(windowsRecommendation == "open" && state.lastRecommendation == "close"){
        state.lastRecommendation = windowsRecommendation
        if(contacts && !openWindows){
            sendMessage("It is $state.lastInTemp°F inside and $state.lastOutTemp°F outside. Open windows.")
        }else if(contacts){
            log.trace ("It is $state.lastInTemp°F inside and $state.lastOutTemp°F outside, but windows are already open.")	
        }else{
            sendMessage("It is $state.lastInTemp°F inside and $state.lastOutTemp°F outside. Open windows and skylights.")
        }
    }
  	log.info "The temperature is $state.inside inside at $state.lastInTemp°F. It is $state.lastOutTemp°F outside and the trend is $state.trend. The day is $state.day with a forecasted high of $state.highTemp°F. The last window recommendation was $state.lastRecommendation and the current recommendation is $windowsRecommendation based on logic $currentLogic."
	sendNotificationEvent("The temperature is $state.inside inside at $state.lastInTemp°F. It is $state.lastOutTemp°F outside and the trend is $state.trend. The day is $state.day with a forecasted high of $state.highTemp°F. The last window recommendation was $state.lastRecommendation and the current recommendation is $windowsRecommendation based on logic $currentLogic.")
}

def weatherCheck() {
	def json = getWeatherFeature("forecast", zipCode)
	def highTemp = json?.forecast?.simpleforecast?.forecastday?.high?.fahrenheit[0]

	if ( highTemp ) {
    	log.trace "High Temp: $highTemp (WeatherUnderground)"
		return highTemp
	} else {
		log.warn "Did not get a high temp: $json"
		return false
	}
}

private sendMessage(msg) {
	if ( sendPush == "Yes" ) {
		log.debug( "sending push message" )
		sendPush( msg )
        sendEvent(linkText:app.label, descriptionText:msg, eventType:"SOLUTION_EVENT", displayed: true, name:"summary")
	}

	if ( phone1 ) {
		log.debug( "sending text message" )
		sendSms( phone1, msg )
	}

	log.info msg
}