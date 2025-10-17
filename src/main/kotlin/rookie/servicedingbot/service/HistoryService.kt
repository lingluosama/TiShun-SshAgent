package rookie.servicedingbot.service

import org.springframework.stereotype.Service

interface HistoryService{

    fun getRecentHistoryByMemoryLimit(key:String,kb:Long):List<String>

}