/**
 *  Window Forecast
 *	When Good Morning! is run, checks if today's forecasted temperature is higher than a preset comfort zone and notifies you to close windows.
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
    	input "insideTemp", "capability.temperatureMeasurement", required: false, title: "Inside temperature"
        input "outsideTemp", "capability.temperatureMeasurement", required: false, title: "Outside temperature"
    }
	section( "Set the temperature range for your comfort zone..." ) {
		input "minTemp", "number", title: "Minimum inside temperature"
		input "maxTemp", "number", title: "Maximum inside temperature"
	}
	section( "Select windows to check..." ) {
		input "contacts", "capability.contactSensor", multiple: true, required: false
	}
	section( "Set your location" ) {
		input "zipCode", "text", title: "Zip code"
	}
	section( "Notifications" ) {
		input "sendPush", "enum", title: "Send a push notification?", metadata:[values:["Yes","No"]], required:true
		input "retryPeriod", "number", title: "Minutes between notifications:"
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
    subscribe(insideTemp, "temperature", temperatureHandler)
    subscribe(outsideTemp, "temperature", temperatureHandler)
    state.lastRecommendation = "don't know" //remembers last state in persistent memory
    state.lastOutTemp = outsideTemp.latestValue("temperature")
    state.lastInTemp = insideTemp.latestValue("temperature")
    state.lastInTemp = state.lastInTemp.toInteger()
    state.trend = "not sure yet"
    def highTempStr = weatherCheck()
   	state.highTemp = highTempStr.toInteger()
    log.trace ("Window Forecast started")
}

def temperatureHandler(evt){
	log.debug "Window Forecast saw that ${evt.displayName} changed to ${evt.value}°F"
    def inTemp = insideTemp.latestValue("temperature")
    def outTemp = outsideTemp.latestValue("temperature")
    def currentLogic = 0
    def windowsRecommendation = "not sure yet"
    def day = "don't know yet"
    def inside = "don't know yet"
	def openWindows = contacts.findAll { it?.latestValue("contact") == 'open' }
    


	//DETERMINE TREND    
    if(state.lastOutTemp-outTemp >= 1 || outTemp-state.lastOutTemp >= 1){
        if(state.lastOutTemp > outTemp){
            state.trend = "decreasing"
        }
        if(state.lastOutTemp < outTemp){
            state.trend = "increasing"
        }
    	state.lastOutTemp = outTemp
    }
    
    //INSIDE TEMP FILTERING
    if(state.lastInTemp-inTemp > 1){
    	inTemp = state.lastInTemp-1
        log.trace ("Inside temp is filtered to $inTemp°F because it decreased by more than the maximum amount")
    }
    if(inTemp-state.lastInTemp > 1){
    	inTemp = state.lastInTemp+1
        log.trace ("Inside temp is filtered to $inTemp°F because it increased by more than the maximum amount")
    }
    if(state.lastInTemp-inTemp >= 1 || inTemp-state.lastInTemp >= 1){
    	state.lastInTemp = inTemp
        log.trace ("Inside temp of $inTemp°F was saved to lastInTemp")
       	
        //CHECK TODAY'S HIGH TEMP SINCE THE INSIDE TEMP CHANGED   
        def highTempStr = weatherCheck()
        state.highTemp = highTempStr.toInteger()
        
    }else{
    	inTemp = state.lastInTemp
        log.trace ("Inside temp is filtered to $inTemp°F because it hasn't changed by the minimum amount.")
    }
   	
	//READABILITY DEFINITIONS
    //INSIDE
	if(inTemp > maxTemp){
    	inside = "high"
    }else if(inTemp < minTemp){
    	inside = "low"
    }else{
    	inside = "comfortable"
    }
    
    //DAY
    if(state.highTemp > maxTemp){
        day = "warm"
    }else if(state.highTemp < minTemp){
        day = "cool"
    }else{
        day = "comfortable"
    }

	//DETERMINE LOGIC
	if(inTemp == outTemp){
    	currentLogic = 2 //2,5,8,11,14,17,20,23,26,29,32,35,38,41,44,47,50,53
        windowsRecommendation = "don't care"
    }else if(inside == "high"){
    	if(inTemp > outTemp){
        	currentLogic = 1 //1,10,19,28,37,46
       		windowsRecommendation = "open"
        }
        if(inTemp < outTemp){
        	currentLogic = 3 //3,12,21,30,39,48
            windowsRecommendation = "close"
        }
    }else if(day == "warm"){
    	if(state.trend == "increasing"){
        	if(inside == "comfortable"){
            	if(inTemp > outTemp){
                	currentLogic = 4 
       				windowsRecommendation = "open"
                }
                if(inTemp < outTemp){
                	currentLogic = 6 
		       		windowsRecommendation = "close"
                }
            }else if(inside == "low"){
            	if(inTemp > outTemp){
                	currentLogic = 7 
       				windowsRecommendation = "open"
                }
                if(inTemp < outTemp){
                	currentLogic = 9 
		       		windowsRecommendation = "close"
                }
            }
        }else if(state.trend == "decreasing"){
        	if(inside == "comfortable"){
            	if(inTemp > outTemp){
                	currentLogic = 13 
       				windowsRecommendation = "don't care"
                }
                if(inTemp < outTemp){
                	currentLogic = 15 
		       		windowsRecommendation = "close"
                }
            }else if(inside == "low"){
            	if(inTemp > outTemp){
                	currentLogic = 16 
       				windowsRecommendation = "don't care"
                }
                if(inTemp < outTemp){
                	currentLogic = 18 
		       		windowsRecommendation = "don't care"
                }
            }
        }
    }else if(day == "comfortable"){
    	if(state.trend == "increasing"){
        	if(inside == "comfortable"){
            	if(inTemp > outTemp){
                	currentLogic = 22 
       				windowsRecommendation = "don't care"
                }
                if(inTemp < outTemp){
                	currentLogic = 24 
		       		windowsRecommendation = "close"
                }
            }else if(inside == "low"){
            	if(inTemp > outTemp){
                	currentLogic = 25 
       				windowsRecommendation = "don't care"
                }
                if(inTemp < outTemp){
                	currentLogic = 27 
		       		windowsRecommendation = "don't care"
                }
            }
        }else if(state.trend == "decreasing"){
        	if(inside == "comfortable"){
            	if(inTemp > outTemp){
                	currentLogic = 31 
       				windowsRecommendation = "don't care"
                }
                if(inTemp < outTemp){
                	currentLogic = 33 
		       		windowsRecommendation = "don't care"
                }
            }else if(inside == "low"){
            	if(inTemp > outTemp){
                	currentLogic = 34 
       				windowsRecommendation = "close"
                }
                if(inTemp < outTemp){
                	currentLogic = 36 
		       		windowsRecommendation = "open"
                }
            }
        }
    }else if(day == "cool"){
    	if(state.trend == "increasing"){
        	if(inside == "comfortable"){
            	if(inTemp > outTemp){
                	currentLogic = 40 
       				windowsRecommendation = "close"
                }
                if(inTemp < outTemp){
                	currentLogic = 42 
		       		windowsRecommendation = "open"
                }
            }else if(inside == "low"){
            	if(inTemp > outTemp){
                	currentLogic = 43 
       				windowsRecommendation = "close"
                }
                if(inTemp < outTemp){
                	currentLogic = 45 
		       		windowsRecommendation = "open"
                }
            }
        }else if(state.trend == "decreasing"){
        	if(inside == "comfortable"){
            	if(inTemp > outTemp){
                	currentLogic = 49 
       				windowsRecommendation = "don't care"
                }
                if(inTemp < outTemp){
                	currentLogic = 51 
		       		windowsRecommendation = "don't care"
                }
            }else if(inside == "low"){
            	if(inTemp > outTemp){
                	currentLogic = 52 
       				windowsRecommendation = "close"
                }
                if(inTemp < outTemp){
                	currentLogic = 54 
		       		windowsRecommendation = "open"
                }
            }
        }
    }
	log.info ("It is $inside inside at $inTemp°F. It is $outTemp°F outside and the trend is $state.trend. The day will be $day with a forecasted high of $state.highTemp°F.")
 
	//CHECK FOR STATE CHANGE
	if(state.lastRecommendation != windowsRecommendation){
        //CLOSE WINDOWS
        if(windowsRecommendation == "close"){
            state.lastRecommendation = windowsRecommendation
            if(contacts && openWindows){
            	sendMessage("It is $inTemp°F inside and $outTemp°F outside. Close ${openWindows.join(', ')} based on logic $currentLogic.")
            }else if(contacts){ 
            	log.trace ("It is $inTemp°F inside and $outTemp°F outside, but windows are already closed based on logic $currentLogic.")
        	}else sendMessage("It is $inTemp°F inside and $outTemp°F outside. Close windows and skylights based on logic $currentLogic.")
        }
        //OPEN WINDOWS
        if(windowsRecommendation == "open"){
            state.lastRecommendation = windowsRecommendation
            if(contacts && !openWindows){
            	sendMessage("It is $inTemp°F inside and $outTemp°F outside. Open windows based on logic $currentLogic.")
            }else if(contacts){
            	log.trace ("It is $inTemp°F inside and $outTemp°F outside, but windows are already open based on logic $currentLogic.")	
        	}else{
            	sendMessage("It is $inTemp°F inside and $outTemp°F outside. Open windows and skylights based on logic $currentLogic.")
            }
        }
    }else{
    	log.trace "It is $inTemp°F inside and $outTemp°F outside. The last recommendation was $state.lastRecommendation and the current recommendation is $windowsRecommendation based on logic $currentLogic"
	}
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