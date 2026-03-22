package com.loopchat.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loopchat.app.ui.theme.*

data class PollOptionUI(
    val id: String,
    val text: String,
    val voteCount: Int,
    val isVotedByMe: Boolean
)

@Composable
fun PollBubble(
    question: String,
    options: List<PollOptionUI>,
    totalVotes: Int,
    isMultipleChoice: Boolean,
    isFromMe: Boolean,
    onVote: (optionId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = if (isFromMe) Color.White else TextPrimary
    val secondaryContentColor = if (isFromMe) Color.White.copy(alpha = 0.8f) else TextSecondary

    Column(modifier = modifier.width(300.dp).padding(vertical = 8.dp)) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Poll, 
                    contentDescription = "Poll", 
                    tint = if (isFromMe) Color.White else Primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = question,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                lineHeight = 22.sp
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        val subText = if (isMultipleChoice) "Select multiple answers" else "Select one answer"
        Text(
            text = subText,
            style = MaterialTheme.typography.labelSmall,
            color = secondaryContentColor
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Options Strip
        options.forEach { option ->
            val percentage = if (totalVotes > 0) (option.voteCount.toFloat() / totalVotes) else 0f
            val animatedWidth by animateFloatAsState(
                targetValue = percentage,
                animationSpec = tween(durationMillis = 800),
                label = "PollBar"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isFromMe) Color.Black.copy(alpha = 0.15f) else SurfaceVariant)
                    .clickable { onVote(option.id) }
            ) {
                // Animated Fill Bar
                if (totalVotes > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedWidth)
                            .height(52.dp)
                            .background(if (isFromMe) Color.White.copy(alpha = 0.25f) else Primary.copy(alpha = 0.15f))
                    )
                }

                // Content
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (option.isVotedByMe) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = null,
                        tint = if (option.isVotedByMe) (if (isFromMe) Color.White else Primary) else (if (isFromMe) Color.White.copy(alpha = 0.6f) else TextMuted),
                        modifier = Modifier.size(22.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Text(
                        text = option.text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (option.isVotedByMe) FontWeight.Bold else FontWeight.Medium,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (totalVotes > 0) {
                        Text(
                            text = "${(percentage * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (option.isVotedByMe) (if (isFromMe) Color.White else Primary) else contentColor
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$totalVotes votes",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = secondaryContentColor
            )
            
            Text(
                text = "View details",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isFromMe) Color.White else Primary,
                modifier = Modifier.clickable { /* Show voters */ }
            )
        }
    }
}
