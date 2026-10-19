const screens={},primary={},secondary=[],cross=[],created=[];
const existing=page.children.filter(n=>n.type==='FRAME'&&n.name.startsWith('11.'));
if(existing.length)throw new Error('V4 already exists: inspect before rebuilding.');
for(let index=0;index<data.length;index++){
 const d=data[index],s=screen('11.'+(index+1),d.title,0,0);s.x=14200+(index%7)*450;s.y=100+Math.floor(index/7)*1500;s.name='11.'+(index+1)+' / '+d.group+' / '+d.title;screens[d.key]=s;created.push(s);
 const status=s.children[0];status.primaryAxisAlignItems='SPACE_BETWEEN';status.children[1].characters='▴  ▰  100%';
 const c=content(s,d.title,d.sub,d.group.toUpperCase(),86);c.itemSpacing=18;
 if(d.visual==='scanner'){const view=box(c,'Camera viewfinder',342,240,'bg_surface_2',22);const frame=box(view,'QR alignment frame',190,190,null,16);border(frame,'primary_coral');frame.x=76;frame.y=25;const t=txt(frame,'Align ticket QR here',13,'text_secondary','Medium',150);t.x=20;t.y=80;}
 for(const [title,body]of d.cards||[])card(c,title,body);
 for(const [l,v]of d.fields||[])field(c,l,v);
 for(const [labelText,to]of d.secondary||[]){let b=button(c,labelText,true);secondary.push({node:b,to});}
 s.resize(390,Math.max(844,Math.ceil(c.y+c.height+125)));primary[d.key]=bottom(s,d.cta).children[0];
 const caption=txt(page,s.name,12,'text_secondary','Medium',390);caption.x=s.x;caption.y=s.y-25;
}
async function dest(key){return key.startsWith('@')?await figma.getNodeByIdAsync(key.slice(1)):screens[key];}
async function link(n,d){if(!n||!d)throw new Error('Missing prototype endpoint');await n.setReactionsAsync([{trigger:{type:'ON_CLICK'},actions:[{type:'NODE',destinationId:d.id,navigation:'NAVIGATE',transition:null}]}]);ids.push(n.id);cross.push({source:n.id,destination:d.id});}
for(const d of data)await link(primary[d.key],await dest(d.to));for(const e of secondary)await link(e.node,await dest(e.to));
async function existingLink(screenId,needle,target){let s=await figma.getNodeByIdAsync(screenId);const matches=s.findAll(n=>n.name===needle||(n.type==='TEXT'&&n.characters===needle));if(!matches.length)return {screenId,needle,missing:true};for(const n of matches)await link(n,await dest(target));return {screenId,needle,count:matches.length};}
const entries=[];
for(const [sid,name,target]of [
 ['5:13','Already have an account? Log In','login'],['5:38','Already have an account? Log In','login'],['5:63','Already have an account? Log In','login'],
 ['7:186','Button / Send Meetup Invite','meetup_invite'],['19:423','Chip / Venues','saved_venues'],['19:423','Chip / People','saved_people'],
 ['19:335','Button / Block unavailable dates','calendar_block'],['19:257','Button / Withdraw request','withdraw'],['19:154','Chip / ○ Debit or credit card','card_payment'],
 ['19:82','Acoustic evening & coffee','waitlist'],['14:196','Payout setup','payout_setup']])entries.push(await existingLink(sid,name,target));
async function entry(sid,labelText,key){let s=await figma.getNodeByIdAsync(sid);let c=s.children.find(n=>n.name==='Screen content');let b=button(c,labelText,true);await link(b,screens[key]);let dock=s.children.find(n=>n.name==='Sticky actions'||n.name==='Bottom navigation');if(dock&&c.y+c.height>dock.y-18){let delta=Math.ceil(c.y+c.height-dock.y+24);s.resize(s.width,s.height+delta);dock.y+=delta;ids.push(s.id,dock.id);}return b;}
await entry('19:480','Open support conversation','support_reply');
await entry('14:196','View business review outcome','venue_changes');
await entry('19:48','Your waitlist spot is available','waitlist_offer');
await entry('19:48','Your event has been rescheduled','event_rescheduled_guest');
await entry('19:48','Event cancelled · Refund update','event_cancelled_guest');
await entry('19:48','Your venue has been approved','venue_approved');
await entry('7:120','View meetup invitation','meetup_invite');
const heading=txt(page,'10 / Complete journeys · access, hosting & event day',36,'text_primary','Bold');heading.x=14200;heading.y=-70;
const board=stack(page,'Flow review / alternate outcomes',342,14,'bg_canvas',20);board.x=13750;board.y=6400;board.cornerRadius=22;txt(board,'Review alternate outcomes',22,'text_primary','SemiBold',302);line(board,'Prototype scenario selector. These are alternative states, not simultaneous results.','text_secondary',13);
for(const [labelText,key]of [['Venue approved','venue_approved'],['Venue needs changes','venue_changes'],['Venue rejected','venue_rejected'],['Invalid entry ticket','checkin_invalid'],['Ticket already used','checkin_used'],['Event cancelled','event_cancelled_guest'],['Waitlist offer expired','waitlist_expired'],['Payout failed','payout_failed']])await link(button(board,labelText,true),screens[key]);
const counts={},fonts=new Set(),issues=[];for(const s of created){for(const n of s.findAll(()=>true)){counts[n.type]=(counts[n.type]||0)+1;if(n.type==='TEXT'){fonts.add(n.fontName.family);if(n.width>n.parent.width+1)issues.push({screen:s.name,id:n.id,reason:'text exceeds parent width'});}}const c=s.children.find(n=>n.name==='Screen content'),b=s.children.find(n=>n.name==='Sticky actions');if(c.y+c.height>b.y-12)issues.push({screen:s.name,reason:'content overlaps CTA'});if(s.height>1420)issues.push({screen:s.name,reason:'row spacing too small'});}
return {createdNodeIds:ids,screens:data.map(d=>({key:d.key,id:screens[d.key].id,name:screens[d.key].name,x:screens[d.key].x,y:screens[d.key].y,width:390,height:screens[d.key].height})),screenCount:created.length,counts,fonts:[...fonts],issues,entries,links:cross,reviewBoardId:board.id};
