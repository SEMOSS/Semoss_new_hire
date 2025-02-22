
build_valid_query<-function(db,joins,text,root_fn="nldr"){
	library(igraph)
	library(data.table)
	library(SteinerNet)
	KEYWORDS<-c("sum","average","count","min","max",'where','group','by','having','order')
	AGGR<-c("sum","average","count","min","max")
	words<-unlist(strsplit(text," "))
	words<-words[nchar(words)>0]
	cols<-unique(db[tolower(db$Column) %in% tolower(words),"Column"])
	# remove keywords from column names
	cols<-cols[!(tolower(cols) %in% KEYWORDS)]
	# Match cases for columns and use handle quotes and choose the column with a single case
	r<-assuage_words(words,cols)
	words<-r[[1]]
	cols<-r[[2]]
	
	stmnt_parts<-partition_request(words)
	validated_parts<-list()
	part_names<-names(stmnt_parts)

	valid<-list()
	valid[[1]]<-vector()
	valid[[2]]<-vector()
	if(length(part_names)>0){
		for(j in 1:length(part_names)){
			part<-stmnt_parts[[part_names[j]]]
			if(part_names[j]=='select'){
				valid<-validate_select(db,part,cols,valid)
				validated_parts[[part_names[j]]]<-unlist(valid[2])
				select_part<-validated_parts[[part_names[j]]]
				# if group is empty enforce its validation 
				if(!('group' %in% part_names)){
					valid<-validate_group(db,vector(),select_part,cols,valid)
					if(length(valid[[2]])>0){
						validated_parts[[part_names[j]]]<-unlist(valid[2])
					}
				}
			}else if(part_names[j]=='where'){
				valid<-validate_where(db,part,cols,valid)
				if(length(valid[[2]])>0){
					validated_parts[[part_names[j]]]<-unlist(valid[2])
				}
			}else if(part_names[j]=='group'){
				valid<-validate_group(db,part,select_part,cols,valid)
				if(length(valid[[2]])>0){
					validated_parts[[part_names[j]]]<-unlist(valid[2])
				}
			}else if(part_names[j]=='having'){
				valid<-validate_having(db,part,cols,valid)
				if(length(valid[[2]])>0){
					validated_parts[[part_names[j]]]<-unlist(valid[2])
				}
			}else if(part_names[j]=='order'){
				valid<-validate_order(db,part,cols,valid)
				if(length(valid[[2]])>0){
					validated_parts[[part_names[j]]]<-unlist(valid[2])
				}
			}
		}
		if(length(valid[[1]])>0){
			out<-data.frame(error=valid[[1]])
			
		}else{
			# The request has been validated_parts
			# Now we have to use the validated components to construct query
			out<-process_valid_statement(db,joins,validated_parts,cols,root_fn)
		}
	}else{
		out<-data.frame(error='request does not contain required sections')
	}
	#write.csv(out,"query_object.csv")
	gc()
	return(out)
}

assuage_words<-function(words,cols){
	# words in single quotes are not columns, mostly for values that matching the column names
	ind<-which(substring(words,1,1)=="'" & substring(words,nchar(words))=="'")
	if(length(ind)>0){
		words[ind]<-substring(words[ind],2,nchar(words[ind])-1)
	}
	# make sure the column names has the propper capitalization
	chosen_cols<-vector()
	#processed_ind<-vector()
	if(length(cols)>0){
		# exact match
		cols_ind<-which(cols %in% words)
		if(length(cols_ind)>0){
			for(i in 1:length(cols_ind)){
				ind<-which(words %in% cols[cols_ind[i]])
				words[ind]<-cols[cols_ind[i]]
				chosen_cols<-append(chosen_cols,cols[cols_ind[i]])
			}
		}
		cols<-cols[!(tolower(cols) %in% tolower(chosen_cols))]
		
		cols_ind<-which(tolower(cols) %in% tolower(words))
		if(length(cols_ind)>0){
			for(i in 1:length(cols_ind)){
				ind<-which(tolower(words) %in% tolower(cols[cols_ind[i]]))
				words[ind]<-cols[cols_ind[i]]
				chosen_cols<-append(chosen_cols,cols[cols_ind[i]])
			}
		}
	}
	out<-list()
	out[[1]]<-words
	out[[2]]<-chosen_cols
	return(out)
}

process_valid_statement<-function(db,joins,stmnt_parts,cols,root_fn="nldr"){

	# initialize statement container
	p<-data.table(query=integer(),appid=character(),appid2=character(),part=character(),item1=character(),item2=character(),
	item3=character(),item4=character(),item5=character(),item6=character(),item7=character())
	# Retrieve cluster info
	partition<-readRDS(paste0(root_fn,"_membership.rds"))
	# limit to potential candidates
	cand_tbls<-db[db$Column %in% cols,"Table"]
	# index potential clusters
	ind<-which(names(partition) %in% cand_tbls)
	# pontial clusters
	clusters<-unique(unname(partition[ind]))
	if(length(clusters)>0){
		k<-1
		for(i in 1:length(clusters)){
			# make sure that all columns present in the cluster
			tbls<-names(partition[partition==clusters[i]])
			tbls_cols<-unique(db[db$Table %in% tbls,"Column"])
			if(all(cols %in% tbls_cols)){
				cur_db<-db[db$Table %in% tbls,]
				cur_joins<-joins[joins$tbl1 %in% tbls & joins$tbl2 %in% tbls,]
				cluster_joins<-build_joins(cols,cur_joins,cur_db)
				
				# process validated statement parts and accumulate them for a given cluster
				part_names<-names(stmnt_parts)
				out<-list()
				if(length(part_names)>0){
					for(j in 1:length(part_names)){
						part<-stmnt_parts[[part_names[j]]]
						if(part_names[j]=='select'){
							p<-process_select(cur_db,part,cols,p,k)
							p<-process_from(cluster_joins,p,k)
						}else if(part_names[j]=='where'){
							p<-process_where(cur_db,part,cols,p,k)
						}else if(part_names[j]=='group'){
							p<-process_group(cur_db,part,cols,p,k)
						}else if(part_names[j]=='having'){
							p<-process_having(cur_db,part,cols,p,k)
						}else if(part_names[j]=='order'){
							p<-process_order(cur_db,part,cols,p,k)
						}
					}
				}
				k<-k+1
			}
		}
	}
	gc()
	return(p)

}

assemble_validated_stmnt<-function(validated_parts){
	part_names<-names(validated_parts)
	for(i in 1:length(validated_parts)){
		part<-validated_parts[[part_names[i]]]
		if(part_names[i]=='select'){
			txt<-paste(part,collapse=' ')
		}else if(part_names[i]=='where'){
			txt<-paste0(txt,' where ',paste(part,collapse=' '))
		}else if(part_names[i]=='group'){
			txt<-paste0(txt,' group by ',paste(part,collapse=' '))
		}else if(part_names[i]=='having'){
			txt<-paste0(txt,' having ',paste(part,collapse=' '))
		}else if(part_names[i]=='order'){
			txt<-paste0(txt,' order by ',paste(part,collapse=' '))
		}
	}
	return(txt)
}

validate_select<-function(db,part,cols,valid){
	AGGR<-c("sum","average","count","min","max")
	
	p<-valid[[1]]
	errs<-part[!(tolower(part) %in% tolower(cols)) & !(tolower(part) %in% AGGR)]
	if(length(errs)>0){
		part<-part[!(part %in% errs)]
	}
	if(length(part)>0){
		ind<-which(tolower(part) %in% tolower(AGGR))
		if(length(ind)>0){
			for(i in 1:length(ind)){
				if(ind[i]==length(part)){
					p[length(p)+1]<-paste0('aggregation ',part[ind[i]],' column name is missing')
				}else{
					if(!(tolower(part[ind[i]+1]) %in% tolower(cols))){
						p[length(p)+1]<-paste0('aggregation ',part(ind[i]),' column name is missing')
					}
				}
			}
		}	
	}else{
		p[length(p)+1]<-'select part is empty'
	}
	valid[[1]]<-p
	valid[[2]]<-part
	return(valid)
}

validate_where<-function(db,part,cols,valid){
	OPS<-c("<","<=",">",">=","<>","=")
	
	p<-valid[[1]]
	real_part<-character()
	if(length(part)>0){
		cur_part<-part
		while(length(cur_part)>0){
			if(length(cur_part)<3){
				p[length(p)+1]<-'where clause is incorrect'
				break
			}else{
				cur_cols<-intersect(tolower(cur_part[1]),tolower(cols))
				if(length(cur_cols) > 0 & cur_part[2] %in% OPS){
					if(tolower(cur_part[3]) %in% c('min','max')){
						if(length(cur_part)>=4){
							if(tolower(cur_part[4])==tolower(cur_cols)){
								# the fourth element is the same column than the first element
								if(length(cur_part)==4) {
									real_part<-append(real_part,cur_part)
									break
								}else{
									real_part<-append(real_part,cur_part[,1:4])
									cur_part<-part[5:length(part)]
								}
							}else{
								# the fourth element is a different column as the first element 
								p<-'where clause is incorrect'
								break
							}
						}else{
							# the third element is min/max and the fourth element is missing
							p<-'where clause is incorrect'
							break
						}
					}else{
						# the third element is value
						if(length(cur_part)==3) {
							real_part<-append(real_part,cur_part)
							break
						}else{
							real_part<-append(real_part,cur_part[1:3])
							cur_part<-part[4:length(part)]
							ind<-which(cur_part %in% cols)
							if(length(ind)>0){
								cur_part<-cur_part[ind[1]:length(cur_part)]
							}else{
								break
							}
						}
					}
				}else{
					# lcuse is not starting with 'column' 'operation'
					p[length(p)+1]<-'where clause is incorrect'
					break
				}
			}
		}
	}else{
		part<-character(0)
	}
	valid[[1]]<-p
	valid[[2]]<-real_part
	return(valid)
}

validate_group<-function(db,part,select_part,cols,valid){
	AGGR<-c("sum","average","count","min","max")
	
	p<-valid[[1]]
	if(length(part)>0){
		errs<-part[!(tolower(part) %in% tolower(cols))]
		if(length(errs)>0){
			part<-part[!(part %in% errs)]
		}
	}
	aggr_ind<-which(tolower(select_part) %in% AGGR)
	# if selec has aggr the group must have singles in select
	if(length(aggr_ind)>0){
		ind<-which(tolower(select_part) %in% tolower(cols))
		if(length(ind)>0){
			select_cols<-vector()
			for(i in 1:length(ind)){
				if(ind[i] > 1){
					if(!(tolower(select_part[ind[i]-1]) %in% AGGR)){
						select_cols[length(select_cols)+1]<-select_part[ind[i]]
					}
				}else{
					select_cols[length(select_cols)+1]<-select_part[ind[i]]
				}
			}
			if(length(select_cols)>0){
				if(!any(tolower(select_cols) %in% tolower(part))){
					if(length(select_cols)==1){
						prefix<-'column '
					}else{
						prefix<-'columns '
					}
					p[length(p)+1]<-paste0(prefix,paste(select_cols,collapse=', '),' must be present in the group clause')
				}
			}
		}
		
	}else{
		# there is no aggregation in select the group must be empty
		part<-character(0)
	}
	valid[[1]]<-p
	valid[[2]]<-part
	return(valid)
}

validate_having<-function(db,part,cols,valid){
	AGGR<-c("sum","average","count","min","max")
	OPS<-c("<","<=",">",">=","<>","=")
	
	p<-valid[[1]]
	if(length(part)>0){
		cur_part<-part
		while(length(part)>0){
			if(length(cur_part)<4){
				p[length(p)+1]<-'having clause is incorrect'
				break
			}else{
				if(tolower(cur_part[1]) %in% AGGR & tolower(cur_part[2]) %in% tolower(cols) & cur_part[3] %in% OPS){
					if(length(cur_part)>4){
						cur_part<-part[5:length(part)]
					}else{
						break
					}
				}else{
					p[length(p)+1]<-'having clause is incorrect'
					break
				}
			}
		}
	}else{
		part<-character(0)
	}
	valid[[1]]<-p
	valid[[2]]<-part
	return(valid)
}

validate_order<-function(db,part,cols,valid){

	p<-valid[[1]]
	if(length(part)>0){
		errs<-part[!(tolower(part) %in% tolower(cols)) & !(tolower(part) %in% c('ascending','descending'))]
		if(length(errs)>0){
			part<-part[!(part %in% errs)]
		}
		ind<-which(tolower(part) %in% tolower(cols))
		if(length(ind)>0){
			for(i in 1:length(ind)){
				if(i < length(ind)){
					if(ind[i+1]-ind[i]>=2 & !(tolower(part[ind[i]+1]) %in% c('ascending','descending'))){
						p[length(p)+1]<-paste0('sorting order may not be ',paste(part[(ind[i]+1):ind[i+1]],collapse=' '))
					}
				}
			}
		}
	}else{
		part<-character(0)
	}
	valid[[1]]<-p
	valid[[2]]<-part
	return(valid)
}